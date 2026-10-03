package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderCollectionsUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderCollectionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = CollectionsTestRepository()
    private val sessions = ActivityTestSessionStore()
    private val store = ViewModelStore()
    private val query = ActivityQuery(collectionsFixture().period.from, collectionsFixture().period.to)
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun close() { store.clear(); dispatcher.scheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    private fun create(port: ProviderCollectionsRepository = repository) = ProviderCollectionsViewModel(
        GetProviderCollectionsUseCase(port), sessions).also { store.put("collections", it) }

    @Test fun `selected Activity period is the only query source and survives returning to the tab`() {
        val vm = create()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.queries.isEmpty())
        val selected = query.copy(from = query.from.minusSeconds(86400))
        vm.selectQuery(selected); dispatcher.scheduler.advanceUntilIdle()
        vm.selectQuery(selected); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(selected), repository.queries)
        assertEquals(selected.from, (vm.uiState.value as ProviderCollectionsUiState.Ready).collections.period.from)
    }
    @Test fun `error retry loading and empty amounts retain current pending groups`() {
        repository.outcome = CollectionsOutcome.Failure.Network
        val vm = create(); vm.selectQuery(query); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ProviderCollectionsUiState.Error(CollectionsOutcome.Failure.Network), vm.uiState.value)
        repository.outcome = null; repository.empty = true; repository.gate = CompletableDeferred()
        vm.retry(); dispatcher.scheduler.runCurrent()
        assertEquals(ProviderCollectionsUiState.Loading, vm.uiState.value)
        vm.retry(); dispatcher.scheduler.runCurrent()
        assertEquals(2, repository.queries.size)
        repository.gate!!.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        val result = (vm.uiState.value as ProviderCollectionsUiState.Ready).collections
        assertEquals(CollectionAmounts(0, 0, 0), result.results)
        assertEquals(3L, result.currentPending.scheduled.orders)
        assertEquals(90000L, result.currentPending.awaitingPayment.amountCents)
        assertEquals(query, repository.queries.last())
    }
    @Test fun `unauthorized clears private session and session loss hides ready summary`() {
        val vm = create(); vm.selectQuery(query); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is ProviderCollectionsUiState.Ready)
        repository.outcome = CollectionsOutcome.Failure.Unauthorized
        vm.retry(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(sessions.getSession())
        assertEquals(ProviderCollectionsUiState.SessionExpired, vm.uiState.value)
    }
    @Test fun `session replacement rejects pending private response`() {
        repository.gate = CompletableDeferred()
        val vm = create(); vm.selectQuery(query); dispatcher.scheduler.runCurrent()
        sessions.saveSession(requireNotNull(sessions.getSession()).copy(accessToken = "replacement"))
        dispatcher.scheduler.runCurrent(); repository.gate!!.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ProviderCollectionsUiState.SessionExpired, vm.uiState.value)
    }
    @Test fun `superseded non cooperative response cannot replace current period`() {
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        val port = object : ProviderCollectionsRepository {
            override suspend fun getCollections(query: ActivityQuery): CollectionsOutcome {
                val gate = CompletableDeferred<Unit>().also { gates += it }
                withContext(NonCancellable) { gate.await() }
                return CollectionsOutcome.Success(collectionsFixture(query))
            }
        }
        val vm = create(port); vm.selectQuery(query); dispatcher.scheduler.runCurrent()
        val next = query.copy(from = query.from.minusSeconds(86400))
        vm.selectQuery(next); dispatcher.scheduler.runCurrent()
        gates[1].complete(Unit); dispatcher.scheduler.runCurrent()
        val latest = vm.uiState.value
        gates[0].complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(latest, vm.uiState.value)
        assertEquals(next.from, (latest as ProviderCollectionsUiState.Ready).collections.period.from)
    }
    @Test fun `collection evolution defaults collapsed and restores expansion independently of query changes`() {
        val saved = androidx.lifecycle.SavedStateHandle()
        val first = ProviderCollectionsViewModel(GetProviderCollectionsUseCase(repository), sessions, saved)
        store.put("first", first)
        assertFalse(first.evolutionExpanded.value)
        first.expandEvolution(true)
        val restored = ProviderCollectionsViewModel(GetProviderCollectionsUseCase(repository), sessions, saved)
        store.put("restored", restored)
        assertTrue(restored.evolutionExpanded.value)
        restored.selectQuery(query); dispatcher.scheduler.advanceUntilIdle()
        restored.selectQuery(query.copy(to = query.to.minusSeconds(60))); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(restored.evolutionExpanded.value)
        restored.expandEvolution(false)
        assertFalse(first.evolutionExpanded.value)
    }

}
