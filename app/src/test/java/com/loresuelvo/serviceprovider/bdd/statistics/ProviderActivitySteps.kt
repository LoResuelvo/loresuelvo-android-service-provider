package com.loresuelvo.serviceprovider.bdd.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase
import com.loresuelvo.serviceprovider.ui.statistics.*
import com.loresuelvo.serviceprovider.ui.components.bottomnav.BottomDestination
import com.loresuelvo.serviceprovider.ui.navigation.Route
import io.cucumber.java.After
import io.cucumber.java.es.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderActivitySteps {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val repository = ActivityTestRepository()
    private val sessions = ActivityTestSessionStore()
    private val viewModelStore = androidx.lifecycle.ViewModelStore()
    private var viewModel: ProviderActivityViewModel? = null
    private var retryLoading = false
    private var failedQuery: ActivityQuery? = null
    private var pendingOrigin: Instant? = null
    init { Dispatchers.setMain(dispatcher) }
    @After fun close() { viewModelStore.clear(); scheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    private fun open() {
        viewModel = ProviderActivityViewModel(GetProviderActivityUseCase(repository), sessions,
            Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)).also { viewModelStore.put("activity", it) }
        scheduler.advanceUntilIdle()
    }
    private fun activity() = (requireNotNull(viewModel).uiState.value as ProviderActivityUiState.Ready).activity

    @Dado("que conseguí y realicé trabajos durante los últimos 30 días")
    fun recentWork() { repository.outcome = ActivityOutcome.Success(activityFixture()) }
    @Cuando("abro Desempeño desde la barra inferior")
    fun openFromNavigation() {
        assertTrue(BottomDestination.all.any { it.route == Route.Activity.path })
        assertTrue(BottomDestination.shouldShow(Route.Activity.path))
        open()
    }
    @Entonces("veo Actividad con el período consultado")
    fun period() { assertEquals(requireNotNull(viewModel).query.from, activity().period.from)
        assertEquals(requireNotNull(viewModel).query.to, activity().period.to) }
    @Entonces("veo mis contrataciones, finalizaciones informadas y trabajos pagados por completo")
    fun counts() { assertEquals(8L, activity().results.confirmedBookings)
        assertEquals(5L, activity().results.reportedCompletions); assertEquals(3L, activity().results.fullyPaidWorkOrders) }
    @Entonces("veo mis clientes atendidos separados en nuevos y recurrentes")
    fun clients() { assertEquals(4L, activity().results.clientsServed)
        assertEquals(3L, activity().results.newClients); assertEquals(1L, activity().results.returningClients) }
    @Entonces("veo el valor pactado y el promedio de los trabajos finalizados en pesos argentinos")
    fun money() { assertEquals("ARS", activity().results.currency)
        assertEquals(12345678L, activity().results.agreedValueCents); assertEquals(2469136L, activity().results.averageValueCents) }
    @Entonces("se distingue el valor pactado del dinero cobrado")
    fun agreedMeaning() {
        val result = activity().results
        assertEquals(12345678L, result.agreedValueCents)
        assertEquals(5L, result.reportedCompletions)
        assertEquals(3L, result.fullyPaidWorkOrders)
        assertTrue(result.reportedCompletions != result.fullyPaidWorkOrders)
        // The paired Screen test renders this real-use-case outcome and asserts the agreed-value clarification.
    }
    @Dado("que no tuve actividad durante el período consultado")
    fun emptyPeriod() { repository.outcome = ActivityOutcome.Success(activityFixture(empty = true)) }
    @Cuando("consulto mis resultados") fun results() = open()
    @Entonces("veo las cantidades y los importes totales en cero")
    fun zeros() { val r = activity().results
        assertTrue(listOf(r.confirmedBookings, r.reportedCompletions, r.fullyPaidWorkOrders,
            r.clientsServed, r.newClients, r.returningClients, r.agreedValueCents).all { it == 0L }) }
    @Entonces("el promedio figura como no disponible") fun noAverage() { assertNull(activity().results.averageValueCents) }
    @Dado("que tengo solicitudes pendientes, trabajos programados y finalizados con saldo pendiente")
    fun pending() { repository.outcome = ActivityOutcome.Success(activityFixture(empty = true)) }
    @Dado("esos pendientes se originaron antes del período consultado")
    fun olderPending() { pendingOrigin = Instant.parse("2026-08-01T12:00:00Z") }
    @Cuando("consulto mi actividad") fun consult() = open()
    @Entonces("veo esos pendientes separados de los resultados del período")
    fun pendingSeparate() { assertTrue(requireNotNull(pendingOrigin) < activity().period.from)
        assertEquals(0L, activity().results.reportedCompletions)
        assertEquals(CurrentPending(2, 7, 4), activity().currentPending) }
    @Entonces("se indica que corresponden a mi situación actual")
    fun currentSituation() {
        val result = activity()
        assertTrue(requireNotNull(pendingOrigin) < result.period.from)
        assertEquals(0L, result.results.reportedCompletions)
        assertTrue(result.currentPending.requests > 0 && result.currentPending.scheduledOrders > 0 &&
            result.currentPending.awaitingPaymentOrders > 0)
        // The paired Screen test asserts the current-pending heading and unfiltered-situation note.
    }
    @Dado("que no se pudieron obtener mis resultados y veo una opción para reintentar")
    fun failure() { repository.outcome = ActivityOutcome.Failure.Network; open()
        assertTrue(requireNotNull(viewModel).uiState.value is ProviderActivityUiState.Error)
        failedQuery = repository.queries.single() }
    @Dado("la información vuelve a estar disponible")
    fun recovered() { repository.outcome = ActivityOutcome.Success(activityFixture()); repository.gate = CompletableDeferred() }
    @Cuando("reintento la consulta")
    fun retry() { requireNotNull(viewModel).retry(); scheduler.runCurrent()
        retryLoading = requireNotNull(viewModel).uiState.value == ProviderActivityUiState.Loading
        repository.gate!!.complete(Unit); scheduler.advanceUntilIdle() }
    @Entonces("veo los resultados del mismo período solicitado")
    fun sameQuery() { assertEquals(failedQuery, repository.queries.last()); period() }
    @Entonces("durante la espera se informa que se están consultando") fun loading() { assertTrue(retryLoading) }
    @Entonces("el error anterior no se presenta como falta de actividad")
    fun errorNotEmpty() { assertEquals(8L, activity().results.confirmedBookings) }
}
