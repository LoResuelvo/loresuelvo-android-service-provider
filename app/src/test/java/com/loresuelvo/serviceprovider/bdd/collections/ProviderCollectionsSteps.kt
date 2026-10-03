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
    private val transactionsRepository = TransactionsTestRepository()
    private val transactions: CollectionTransactionsViewModel
    private var selectedPurpose: CollectionPurpose? = null
    private var expectedRows = emptyList<CollectionTransaction>()
    private var expectedTotalCount = 0L
    private var expectedTotalAmount = 0L
    private var expectedQuery: ActivityQuery? = null
    init {
        Dispatchers.setMain(dispatcher)
        activity = ProviderActivityViewModel(GetProviderActivityUseCase(activityRepository), sessions,
            Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)).also { store.put("activity", it) }
        collections = ProviderCollectionsViewModel(GetProviderCollectionsUseCase(repository), sessions)
            .also { store.put("collections", it) }
        transactions = CollectionTransactionsViewModel(GetCollectionTransactionsUseCase(transactionsRepository), sessions)
            .also { store.put("transactions", it) }
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

    private fun page() = transactions.uiState.value.page!!
    private fun openTransactions() {
        open()
        transactions.selectPeriod(result().period)
        scheduler.advanceUntilIdle()
    }
    @Dado("que tengo varios cobros verificados durante el período") fun severalTransactions() {
        assertTrue(transactionsRepository.queries.isEmpty())
        repository.empty = false
    }
    @Cuando("consulto los movimientos") fun consultTransactions() = openTransactions()
    @Entonces("veo la fecha de verificación, si es seña o saldo y el importe de cada movimiento")
    fun transactionDetails() {
        val row = page().transactions.single()
        assertEquals(activity.query.to.minusSeconds(60), row.verifiedOn)
        assertEquals(CollectionPurpose.BOOKING_DEPOSIT, row.purpose)
        assertEquals(120000L, row.sellerAmountCents)
        assertEquals("ARS", row.currency)
    }
    @Entonces("veo sus referencias de propuesta y orden cuando están disponibles") fun references() {
        assertEquals(8, page().transactions.single().serviceProposalId)
        assertNull(page().transactions.single().workOrderId)
        transactions.loadMore(); scheduler.advanceUntilIdle()
        assertEquals(9, page().transactions[1].serviceProposalId)
        assertEquals(10, page().transactions[1].workOrderId)
    }
    @Entonces("veo la cantidad y el importe total de todos los movimientos del período") fun wholePeriodTotals() {
        assertEquals(3L, page().totalCount); assertEquals(450000L, page().totalAmountCents)
    }
    @Dado("que tengo señas y saldos en el período") fun bothTypes() = openTransactions()
    @Cuando("elijo mostrar {string}") fun filterTransactions(type: String) {
        selectedPurpose = when (type) {
            "Señas" -> CollectionPurpose.BOOKING_DEPOSIT
            "Saldos" -> CollectionPurpose.SERVICE_BALANCE
            "Todos" -> null
            else -> error("Unsupported approved filter")
        }
        transactions.selectPurpose(selectedPurpose); scheduler.advanceUntilIdle()
    }
    @Entonces("veo solamente los movimientos correspondientes") fun filteredRows() {
        assertTrue(page().transactions.isNotEmpty())
        assertTrue(page().transactions.all { selectedPurpose == null || it.purpose == selectedPurpose })
        assertEquals(selectedPurpose, transactionsRepository.queries.last().purpose)
    }
    @Entonces("la cantidad y el importe total corresponden a todos los movimientos de ese tipo") fun filteredTotals() {
        val totals = when (selectedPurpose) {
            CollectionPurpose.BOOKING_DEPOSIT -> 2L to 170000L
            CollectionPurpose.SERVICE_BALANCE -> 1L to 280000L
            null -> 3L to 450000L
        }
        assertEquals(totals.first, page().totalCount); assertEquals(totals.second, page().totalAmountCents)
    }
    @Dado("que todavía quedan movimientos del período por mostrar") fun remainingTransactions() {
        openTransactions()
        val page = page()
        assertNotNull(page.nextCursor)
        expectedRows = page.transactions
        expectedTotalCount = page.totalCount; expectedTotalAmount = page.totalAmountCents
        val following = page.copy(transactions = page.transactions + listOf(
            CollectionTransaction(2, activity.query.to.minusSeconds(120), CollectionPurpose.SERVICE_BALANCE,
                280000, "ARS", 9, 10)), nextCursor = null)
        transactionsRepository.outcomes.add(CollectionTransactionsOutcome.Success(following))
    }
    @Cuando("elijo cargar más") fun loadMore() { transactions.loadMore(); scheduler.advanceUntilIdle() }
    @Entonces("veo los siguientes movimientos del mismo período y tipo sin duplicados") fun continuation() {
        assertEquals(2, page().transactions.size)
        assertEquals(page().transactions.size, page().transactions.map { it.id }.distinct().size)
        val first = transactionsRepository.queries.first(); val last = transactionsRepository.queries.last()
        assertEquals(first.from, last.from); assertEquals(first.to, last.to); assertEquals(first.purpose, last.purpose)
        assertEquals("opaque+/cursor=", last.cursor)
        assertEquals(listOf(3L, 2L), page().transactions.map { it.id })
    }
    @Entonces("conservo los que ya estaba leyendo") fun retainRows() { assertTrue(page().transactions.containsAll(expectedRows)) }
    @Entonces("la cantidad y el importe total no se limitan a los movimientos visibles") fun globalTotals() {
        assertEquals(expectedTotalCount, page().totalCount); assertEquals(expectedTotalAmount, page().totalAmountCents)
        assertTrue(page().totalCount > page().transactions.size)
    }
}
