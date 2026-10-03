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

    private var lastValid: ProviderActivity? = null
    private var expectedDateError: ActivityDateError? = null
    private var selectedGranularity: ActivityGranularity? = null

    @Dado("que estoy consultando mi actividad")
    fun alreadyConsulting() { repository.respondToQuery = true; open(); lastValid = activity() }
    @Cuando("elijo un período válido diferente")
    fun choosePeriod() {
        requireNotNull(viewModel).editDates("2026-08-01", "2026-08-31")
        requireNotNull(viewModel).applyDates(); scheduler.advanceUntilIdle()
    }
    @Entonces("veo los resultados y las fechas del período elegido")
    fun selectedResults() {
        val query = repository.queries.last()
        assertEquals(Instant.parse("2026-08-01T03:00:00Z"), query.from)
        assertEquals(Instant.parse("2026-09-01T03:00:00Z"), query.to)
        period()
        assertEquals(3L, activity().results.confirmedBookings)
        assertEquals(2L, activity().results.reportedCompletions)
        assertEquals(1L, activity().results.fullyPaidWorkOrders)
        assertEquals(200000L, activity().results.agreedValueCents)
        assertNotEquals(requireNotNull(lastValid).results, activity().results)
        assertEquals(3L, activity().evolution.sumOf { it.confirmedBookings })
        assertEquals(2L, activity().evolution.sumOf { it.reportedCompletions })
        assertEquals(1L, activity().evolution.sumOf { it.fullyPaidWorkOrders })
        assertNotEquals(requireNotNull(lastValid).period, activity().period)
    }
    @Entonces("mis pendientes actuales conservan su significado")
    fun pendingUnfiltered() { assertEquals(requireNotNull(lastValid).currentPending, activity().currentPending) }
    @Dado("que estoy eligiendo las fechas de consulta")
    fun choosingDates() = alreadyConsulting()
    @Cuando("selecciono un período {string}")
    fun invalidPeriod(period: String) {
        val dates = when (period) {
            "con fechas invertidas" -> { expectedDateError = ActivityDateError.REVERSED; "2026-09-20" to "2026-09-10" }
            "de más de 365 días" -> { expectedDateError = ActivityDateError.TOO_LONG; "2025-01-01" to "2026-09-30" }
            "que termina en el futuro" -> { expectedDateError = ActivityDateError.FUTURE; "2026-09-01" to "2026-10-04" }
            else -> error("Unexpected period example")
        }
        requireNotNull(viewModel).editDates(dates.first, dates.second)
        requireNotNull(viewModel).applyDates(); scheduler.advanceUntilIdle()
    }
    @Entonces("se explica cómo corregir las fechas")
    fun correction() { assertEquals(expectedDateError, requireNotNull(viewModel).filters.value.dateError) }
    @Entonces("no se reemplazan los últimos resultados válidos")
    fun preservesResults() { assertEquals(lastValid, activity()); assertEquals(1, repository.queries.size) }
    @Dado("que tuve actividad e intervalos sin trabajos durante el período")
    fun workAndGaps() {
        repository.respondToQuery = true; open()
        requireNotNull(viewModel).editDates("2026-07-01", "2026-09-30")
        requireNotNull(viewModel).applyDates(); scheduler.advanceUntilIdle()
    }
    @Cuando("elijo ver la evolución por {string}")
    fun chooseGrouping(grouping: String) {
        selectedGranularity = when (grouping) {
            "día" -> ActivityGranularity.DAY
            "semana" -> ActivityGranularity.WEEK
            "mes" -> ActivityGranularity.MONTH
            else -> error("Unexpected grouping example")
        }
        requireNotNull(viewModel).selectGranularity(requireNotNull(selectedGranularity)); scheduler.advanceUntilIdle()
    }
    @Entonces("distingo contrataciones, finalizaciones informadas y pagos completos")
    fun series() {
        assertEquals(selectedGranularity, repository.queries.last().granularity)
        val buckets = activity().evolution
        assertEquals(activity().results.confirmedBookings, buckets.sumOf { it.confirmedBookings })
        assertEquals(activity().results.reportedCompletions, buckets.sumOf { it.reportedCompletions })
        assertEquals(activity().results.fullyPaidWorkOrders, buckets.sumOf { it.fullyPaidWorkOrders })
    }
    @Entonces("puedo consultar sus cantidades incluyendo los intervalos en cero")
    fun zeroIntervals() {
        val buckets = activity().evolution
        assertTrue(buckets.any { it.confirmedBookings == 0L && it.reportedCompletions == 0L && it.fullyPaidWorkOrders == 0L })
        assertEquals(activity().period.from, buckets.first().from); assertEquals(activity().period.to, buckets.last().to)
        assertTrue(buckets.zipWithNext().all { (left, right) -> left.to == right.from })
    }
    @Dado("que algunas de mis métricas tienen resultados en el período anterior y otras no")
    fun previousActivity() = alreadyConsulting()
    @Cuando("activo la comparación con el período anterior")
    fun compare() { requireNotNull(viewModel).comparePrevious(true); scheduler.advanceUntilIdle() }
    @Entonces("veo ambos períodos de igual duración y sus diferencias")
    fun equalPeriods() {
        val result = activity(); val previous = requireNotNull(result.comparison)
        assertTrue(repository.queries.last().comparePrevious)
        assertEquals(result.period.from, previous.period.to)
        assertEquals(java.time.Duration.between(result.period.from, result.period.to),
            java.time.Duration.between(previous.period.from, previous.period.to))
        assertEquals(result.results.confirmedBookings - previous.results.confirmedBookings, previous.changes.confirmedBookings.absolute)
        assertEquals(100.0, previous.changes.confirmedBookings.percentage!!, 0.0)
    }
    @Entonces("los porcentajes sin una base de comparación figuran como no disponibles")
    fun undefinedPercentages() {
        val previous = requireNotNull(activity().comparison)
        assertEquals(0L, previous.results.fullyPaidWorkOrders); assertNull(previous.changes.fullyPaidWorkOrders.percentage)
        assertNull(previous.results.averageValueCents); assertNull(previous.changes.averageValueCents.absolute)
        assertNull(previous.changes.averageValueCents.percentage)
    }
    @Entonces("mis pendientes actuales no se comparan con el pasado")
    fun noPendingComparison() { pendingUnfiltered() }
    private var retainedQuery: ActivityQuery? = null
    private var retainedFilters: ActivityFilters? = null
    private val savedState = androidx.lifecycle.SavedStateHandle()

    @Dado("que elegí un período y estaba leyendo su evolución")
    fun readingEvolution() {
        repository.respondToQuery = true
        viewModel = ProviderActivityViewModel(GetProviderActivityUseCase(repository), sessions,
            Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC), savedState)
            .also { viewModelStore.put("activity", it) }
        scheduler.advanceUntilIdle()
        choosePeriod()
        requireNotNull(viewModel).selectGranularity(ActivityGranularity.WEEK)
        requireNotNull(viewModel).comparePrevious(true)
        scheduler.advanceUntilIdle()
        requireNotNull(viewModel).expandPeriod(true)
        requireNotNull(viewModel).expandEvolution(true)
        requireNotNull(viewModel).rememberReadingPosition(7, 42)
        retainedQuery = requireNotNull(viewModel).query
        retainedFilters = requireNotNull(viewModel).filters.value
    }
    @Cuando("{string}")
    fun resumeReading(returnAction: String) {
        when (returnAction) {
            "vuelvo a Desempeño después de ver Mensajes" -> Unit // The NavHost retention is verified on Android.
            "giro el dispositivo mientras leo mis datos" -> {
                viewModelStore.clear()
                viewModel = ProviderActivityViewModel(GetProviderActivityUseCase(repository), sessions,
                    Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC), savedState)
                    .also { viewModelStore.put("activity", it) }
            }
            else -> error("Unexpected return action")
        }
        scheduler.advanceUntilIdle()
    }
    @Entonces("continúo en Actividad con el período y las opciones elegidas")
    fun retainedSelection() {
        assertEquals(retainedQuery, requireNotNull(viewModel).query)
        assertEquals(retainedFilters, requireNotNull(viewModel).filters.value)
        assertTrue(requireNotNull(viewModel).periodExpanded.value)
        assertTrue(requireNotNull(viewModel).evolutionExpanded.value)
        period()
    }
    @Entonces("conservo mi posición de lectura")
    fun retainedPosition() {
        assertEquals(7, requireNotNull(viewModel).readingIndex)
        assertEquals(42, requireNotNull(viewModel).readingOffset)
    }
    @Dado("que mi sesión dejó de estar vigente")
    fun expiredSession() {
        repository.respondToQuery = true; open()
        assertTrue(requireNotNull(viewModel).uiState.value is ProviderActivityUiState.Ready)
        repository.respondToQuery = false
        repository.outcome = ActivityOutcome.Failure.Unauthorized
    }
    @Cuando("intento consultar mi actividad")
    fun consultExpired() { requireNotNull(viewModel).retry(); scheduler.advanceUntilIdle() }
    @Entonces("se me solicita ingresar nuevamente")
    fun loginRequired() {
        assertEquals(ProviderActivityUiState.SessionExpired, requireNotNull(viewModel).uiState.value)
        assertNull(sessions.getSession())
    }
    @Entonces("mis estadísticas privadas no quedan visibles")
    fun noPrivateResults() { assertFalse(requireNotNull(viewModel).uiState.value is ProviderActivityUiState.Ready) }

}
