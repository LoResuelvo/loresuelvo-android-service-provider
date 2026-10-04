package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderConversionUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderConversionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = ConversionTestRepository()
    private val sessions = ActivityTestSessionStore()
    private val store = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun close() { store.clear(); dispatcher.scheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    private fun create(port: ProviderConversionRepository = repository) = ProviderConversionViewModel(
        GetProviderConversionUseCase(port), sessions,
        Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)).also { store.put("conversion", it) }
    private fun settle() = dispatcher.scheduler.advanceUntilIdle()
    @Test fun `lazy independent query is captured for retries and duplicate loads are ignored`() {
        val vm = create(); settle(); assertTrue(repository.queries.isEmpty())
        repository.gate = CompletableDeferred()
        vm.open(); dispatcher.scheduler.runCurrent(); vm.open(); vm.retry(); dispatcher.scheduler.runCurrent()
        assertEquals(1, repository.queries.size); assertEquals(ProviderConversionUiState.Loading, vm.uiState.value)
        repository.outcome = ConversionOutcome.Failure.Network
        repository.gate!!.complete(Unit); settle()
        assertEquals(ProviderConversionUiState.Error(ConversionOutcome.Failure.Network), vm.uiState.value)
        repository.outcome = ConversionOutcome.Success(conversionFixture(0, true)); vm.retry(); settle()
        assertEquals(listOf(vm.query, vm.query), repository.queries)
        assertEquals(Instant.parse("2026-09-04T12:00:00Z"), vm.query.from!!.toInstant())
        assertEquals(5L, (vm.uiState.value as ProviderConversionUiState.Ready).conversion.requests.received)
    }
    @Test fun `unauthorized and logout erase private conversion and expansion`() {
        val vm = create(); vm.open(); settle(); vm.expandAdvances(true)
        repository.outcome = ConversionOutcome.Failure.Unauthorized; vm.retry(); settle()
        assertNull(sessions.getSession()); assertEquals(ProviderConversionUiState.SessionExpired, vm.uiState.value)
        assertFalse(vm.advancesExpanded.value); vm.retry(); settle(); assertEquals(2, repository.queries.size)
    }
    @Test fun `session replacement cancels and ignores non cooperative old result`() {
        val gate = CompletableDeferred<Unit>(); var cancelled = false
        val port = object : ProviderConversionRepository {
            override suspend fun getConversion(query: ConversionQuery): ConversionOutcome {
                try { gate.await() } catch (_: CancellationException) {
                    cancelled = true; withContext(NonCancellable) { gate.await() }
                }
                return ConversionOutcome.Success(conversionFixture())
            }
        }
        val vm = create(port); vm.open(); dispatcher.scheduler.runCurrent()
        sessions.saveSession(requireNotNull(sessions.getSession()).copy(accessToken = "replacement-provider"))
        dispatcher.scheduler.runCurrent(); assertTrue(cancelled)
        gate.complete(Unit); settle(); assertEquals(ProviderConversionUiState.SessionExpired, vm.uiState.value)
    }
    @Test fun `logout after loaded conversion erases state and absent session never queries`() {
        val vm = create(); vm.open(); settle(); sessions.clearSession(); settle()
        assertEquals(ProviderConversionUiState.SessionExpired, vm.uiState.value)
        val absent = create(); absent.open(); settle(); assertEquals(1, repository.queries.size)
        assertEquals(ProviderConversionUiState.SessionExpired, absent.uiState.value)
    }
}
