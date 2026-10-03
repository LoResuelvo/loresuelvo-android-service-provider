package com.loresuelvo.serviceprovider.ui.statistics

import com.loresuelvo.serviceprovider.domain.statistics.ActivityOutcome
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderActivityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = ActivityTestRepository()
    private val sessions = ActivityTestSessionStore()
    private val viewModelStore = androidx.lifecycle.ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun close() { viewModelStore.clear(); dispatcher.scheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    private fun create() = ProviderActivityViewModel(GetProviderActivityUseCase(repository), sessions,
        Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)).also { viewModelStore.put("activity", it) }
    @Test fun `unauthorized clears private session and results`() {
        repository.outcome = ActivityOutcome.Failure.Unauthorized
        val vm = create(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(sessions.getSession()); assertEquals(ProviderActivityUiState.SessionExpired, vm.uiState.value)
    }
    @Test fun `session replacement rejects pending response`() {
        repository.gate = CompletableDeferred()
        val vm = create(); dispatcher.scheduler.runCurrent()
        sessions.saveSession(requireNotNull(sessions.getSession()).copy(accessToken = "replacement"))
        dispatcher.scheduler.runCurrent(); repository.gate!!.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ProviderActivityUiState.SessionExpired, vm.uiState.value)
    }
    @Test fun `duplicate retry does not create concurrent queries`() {
        repository.gate = CompletableDeferred()
        val vm = create(); dispatcher.scheduler.runCurrent(); vm.retry(); dispatcher.scheduler.runCurrent()
        assertEquals(1, repository.queries.size)
        repository.gate!!.complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is ProviderActivityUiState.Ready)
    }
}
