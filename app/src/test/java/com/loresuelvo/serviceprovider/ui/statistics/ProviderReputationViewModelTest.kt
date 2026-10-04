package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderReputationUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderReputationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = ReputationTestRepository()
    private val sessions = ActivityTestSessionStore()
    private val store = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun close() { store.clear(); dispatcher.scheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    private fun create(port: ProviderReputationRepository = repository) = ProviderReputationViewModel(
        GetProviderReputationUseCase(port), sessions).also { store.put("reputation", it) }

    @Test fun `opening is lazy and returning does not query again`() {
        val vm = create(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, repository.calls)
        repository.gate = CompletableDeferred()
        vm.open(); dispatcher.scheduler.runCurrent()
        assertEquals(ProviderReputationUiState.Loading, vm.uiState.value)
        vm.open(); vm.retry(); dispatcher.scheduler.runCurrent()
        assertEquals(1, repository.calls)
        repository.gate!!.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(reputationFixture(), (vm.uiState.value as ProviderReputationUiState.Ready).reputation)
        vm.open(); dispatcher.scheduler.advanceUntilIdle(); assertEquals(1, repository.calls)
    }
    @Test fun `failure retries first reviews and empty remains a valid result`() {
        repository.outcome = ReputationOutcome.Failure.Network
        val vm = create(); vm.open(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ProviderReputationUiState.Error(ReputationOutcome.Failure.Network), vm.uiState.value)
        repository.outcome = ReputationOutcome.Success(reputationFixture(3, empty = true))
        vm.retry(); dispatcher.scheduler.advanceUntilIdle()
        val result = (vm.uiState.value as ProviderReputationUiState.Ready).reputation
        assertNull(result.averageRating); assertEquals(0.0, result.coveragePercentage!!, 0.0)
        assertTrue(result.reviews.isEmpty()); assertEquals(2, repository.calls)
    }
    @Test fun `unauthorized clears session and logout hides received comments`() {
        val vm = create(); vm.open(); dispatcher.scheduler.advanceUntilIdle()
        repository.outcome = ReputationOutcome.Failure.Unauthorized
        vm.retry(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(sessions.getSession()); assertEquals(ProviderReputationUiState.SessionExpired, vm.uiState.value)
    }
    @Test fun `session change rejects non cooperative stale response and cancels request`() {
        val gate = CompletableDeferred<Unit>()
        var cancelled = false
        val port = object : ProviderReputationRepository {
            override suspend fun getReputation(): ReputationOutcome {
                try { gate.await() } catch (_: CancellationException) {
                    cancelled = true
                    withContext(NonCancellable) { gate.await() }
                }
                return ReputationOutcome.Success(reputationFixture())
            }
        }
        val vm = create(port); vm.open(); dispatcher.scheduler.runCurrent()
        sessions.saveSession(requireNotNull(sessions.getSession()).copy(accessToken = "other-provider"))
        dispatcher.scheduler.runCurrent(); assertTrue(cancelled)
        assertEquals(ProviderReputationUiState.SessionExpired, vm.uiState.value)
        gate.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ProviderReputationUiState.SessionExpired, vm.uiState.value)
    }
    @Test fun `logout after success erases data and missing session does not query`() {
        val vm = create(); vm.open(); dispatcher.scheduler.advanceUntilIdle()
        sessions.clearSession(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ProviderReputationUiState.SessionExpired, vm.uiState.value)
        vm.retry(); dispatcher.scheduler.advanceUntilIdle(); assertEquals(1, repository.calls)
    }
}
