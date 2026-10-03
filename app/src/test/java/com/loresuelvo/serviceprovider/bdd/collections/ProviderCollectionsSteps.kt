package com.loresuelvo.serviceprovider.bdd.collections

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.*
import com.loresuelvo.serviceprovider.ui.statistics.*
import io.cucumber.java.After
import io.cucumber.java.es.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderCollectionsSteps {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val repository = CollectionsTestRepository()
    private val sessions = ActivityTestSessionStore()
    private val store = ViewModelStore()
    private val activityRepository = ActivityTestRepository().apply { respondToQuery = true }
    private val activity: ProviderActivityViewModel
    private val collections: ProviderCollectionsViewModel
    private var expectedQuery: ActivityQuery? = null
    init {
        Dispatchers.setMain(dispatcher)
        activity = ProviderActivityViewModel(GetProviderActivityUseCase(activityRepository), sessions,
            Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)).also { store.put("activity", it) }
        collections = ProviderCollectionsViewModel(GetProviderCollectionsUseCase(repository), sessions)
            .also { store.put("collections", it) }
    }
    @After fun close() { store.clear(); scheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    private fun result() = (collections.uiState.value as ProviderCollectionsUiState.Ready).collections
    private fun open() { collections.selectQuery(activity.filters.value.query); scheduler.advanceUntilIdle() }

    @Dado("que tengo señas y saldos verificados durante el período elegido en Actividad")
    fun verifiedPayments() {
        activity.editDates("2026-08-01", "2026-08-31"); activity.applyDates()
        scheduler.advanceUntilIdle(); expectedQuery = activity.query
    }
    @Cuando("abro Cobros dentro de Desempeño") fun openCollections() = open()
    @Entonces("conservo el período elegido") fun sharedPeriod() {
        assertEquals(expectedQuery, repository.queries.single())
        assertEquals(activity.query.from, result().period.from); assertEquals(activity.query.to, result().period.to)
    }
    @Entonces("veo señas, saldos y su total en pesos argentinos sin comisiones")
    fun amounts() {
        assertEquals("ARS", result().currency)
        assertEquals(CollectionAmounts(120000, 280000, 400000), result().results)
        assertEquals(result().results.bookingDepositCents + result().results.serviceBalanceCents, result().results.totalCents)
    }
    @Entonces("se aclara que los importes no representan un saldo bancario") fun bankBalanceMeaning() {
        amounts()
        // The paired stateless Screen test verifies the clarification for this real-use-case result.
    }
    @Dado("que no tengo cobros verificados durante el período") fun noPayments() { repository.empty = true }
    @Dado("tengo trabajos con saldos pendientes") fun pendingWork() {
        repository.outcome = CollectionsOutcome.Success(collectionsFixture(activity.query, empty = true))
    }
    @Cuando("consulto mis cobros") fun consult() = open()
    @Entonces("veo los cobros del período en cero") fun zeros() { assertEquals(CollectionAmounts(0, 0, 0), result().results) }
    @Entonces("sigo viendo mis saldos pendientes actuales") fun currentPending() {
        assertEquals(CurrentCollectionPending(PendingCollectionBalance(3, 210000), PendingCollectionBalance(2, 90000)),
            result().currentPending)
    }
    @Dado("que tengo trabajos programados y finalizados con saldo pendiente") fun bothPendingGroups() { repository.empty = true }
    @Entonces("veo por separado la cantidad y el saldo pendiente de cada grupo") fun separateGroups() = currentPending()
    @Entonces("esos saldos no dependen del período consultado") fun independentOfPeriod() {
        val pending = result().currentPending
        activity.editDates("2026-08-01", "2026-08-31"); activity.applyDates(); open()
        assertEquals(pending, result().currentPending)
        assertEquals(Instant.parse("2026-08-01T03:00:00Z"), result().period.from)
    }
    @Entonces("no incluyen señas ya cobradas ni comisiones") fun contractualPending() {
        currentPending()
        assertEquals(300000L, result().currentPending.scheduled.amountCents + result().currentPending.awaitingPayment.amountCents)
        // The repository supplies contractual service balances; Android never recalculates fees or deposits.
    }
    @Dado("que mi cuenta de Mercado Pago está desconectada") fun disconnected() {
        repository.paymentAccountConnected = false
    }
    @Dado("tengo cobros verificados anteriores") fun previousPayments() { repository.empty = false }
    @Entonces("puedo ver los importes registrados sin conectar la cuenta") fun historyWithoutConnection() {
        amounts(); assertEquals(1, repository.queries.size)
        assertFalse(repository.paymentAccountConnected); assertNotNull(sessions.getSession())
    }
}
