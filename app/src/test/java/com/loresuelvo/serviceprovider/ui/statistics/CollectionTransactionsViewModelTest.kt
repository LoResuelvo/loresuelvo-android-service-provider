package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetCollectionTransactionsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionTransactionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = TransactionsTestRepository()
    private val sessions = ActivityTestSessionStore()
    private val store = ViewModelStore()
    private lateinit var model: CollectionTransactionsViewModel
    private val period = collectionsFixture().period
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        model = CollectionTransactionsViewModel(GetCollectionTransactionsUseCase(repository), sessions)
        store.put("transactions", model)
    }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }
    private fun page() = model.uiState.value.page!!
    private fun open() { model.selectPeriod(period); dispatcher.scheduler.advanceUntilIdle() }

    @Test fun `opaque continuation retains rows deduplicates and sorts by date then id with whole filter totals`() {
        open()
        val first = page()
        repository.outcomes.add(CollectionTransactionsOutcome.Success(first.copy(transactions = listOf(
            first.transactions.single(), first.transactions.single().copy(id = 2),
            first.transactions.single().copy(id = 1, verifiedOn = period.to.minusSeconds(120))), nextCursor = null)))
        model.loadMore(); model.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, repository.queries.size)
        assertEquals("opaque+/cursor=", repository.queries.last().cursor)
        assertEquals(repository.queries.first().copy(cursor = "opaque+/cursor="), repository.queries.last())
        assertEquals(listOf(3L, 2L, 1L), page().transactions.map { it.id })
        assertEquals(first.totalCount, page().totalCount); assertEquals(first.totalAmountCents, page().totalAmountCents)
        model.loadMore(); dispatcher.scheduler.advanceUntilIdle(); assertEquals(2, repository.queries.size)
    }
    @Test fun `purpose and period change discard cursor and prior rows and ignore obsolete request`() {
        open()
        repository.gate = CompletableDeferred()
        model.loadMore(); dispatcher.scheduler.runCurrent()
        model.selectPurpose(CollectionPurpose.SERVICE_BALANCE)
        assertNull(model.uiState.value.page)
        repository.gate!!.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertNull(repository.queries.last().cursor)
        assertEquals(CollectionPurpose.SERVICE_BALANCE, repository.queries.last().purpose)
        assertEquals(listOf(2L), page().transactions.map { it.id })
        model.selectPeriod(period.copy(from = period.from.minusSeconds(60)))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(period.from.minusSeconds(60), repository.queries.last().from)
        assertNull(repository.queries.last().cursor)
        val count = repository.queries.size
        model.selectPeriod(period.copy(from = period.from.minusSeconds(60)))
        dispatcher.scheduler.advanceUntilIdle(); assertEquals(count, repository.queries.size)
    }
    @Test fun `page network error keeps previous rows and retry uses same cursor`() {
        open(); val first = page()
        repository.outcomes.add(CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.Network))
        model.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(first, page()); assertEquals(CollectionsOutcome.Failure.Network, model.uiState.value.failure)
        assertFalse(model.uiState.value.restartRequired)
        model.retry(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals("opaque+/cursor=", repository.queries.last().cursor)
        assertEquals(3, page().transactions.size); assertNull(model.uiState.value.failure)
    }
    @Test fun `invalid continuation requires honest restart and replaces previous rows after success`() {
        open()
        repository.outcomes.add(CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.InvalidQuery))
        model.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.uiState.value.restartRequired); assertEquals(1, page().transactions.size)
        model.retry(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(repository.queries.last().cursor); assertFalse(model.uiState.value.restartRequired)
        assertEquals(1, page().transactions.size)
    }
    @Test fun `unauthorized response clears session and all transaction data`() {
        open()
        repository.outcomes.add(CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.Unauthorized))
        model.loadMore(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(sessions.getSession()); assertTrue(model.uiState.value.sessionExpired)
        assertNull(model.uiState.value.page)
    }
    @Test fun `logout during pending continuation cancels request and erases rows`() {
        open(); repository.gate = CompletableDeferred()
        model.loadMore(); dispatcher.scheduler.runCurrent()
        sessions.clearSession(); dispatcher.scheduler.runCurrent()
        repository.gate!!.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(model.uiState.value.sessionExpired); assertNull(model.uiState.value.page)
    }
    @Test fun `first page failure stays failure and filter may recover without a cursor`() {
        repository.outcomes.add(CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.Forbidden))
        open()
        assertNull(model.uiState.value.page); assertEquals(CollectionsOutcome.Failure.Forbidden, model.uiState.value.failure)
        model.selectPurpose(CollectionPurpose.BOOKING_DEPOSIT); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(2L, page().totalCount); assertEquals(170000L, page().totalAmountCents)
        assertNull(repository.queries.last().cursor)
    }
}
