package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderConversionUseCase
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderConversionPeriodTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00.123456789Z"), ZoneOffset.UTC)
    private val sessions = ActivityTestSessionStore()
    private val repository = ConversionTestRepository().apply { respondToQuery = true }
    private val store = ViewModelStore()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun close() { store.clear(); dispatcher.scheduler.advanceUntilIdle(); Dispatchers.resetMain() }
    private fun vm(port: ProviderConversionRepository = repository, saved: SavedStateHandle = SavedStateHandle()) =
        ProviderConversionViewModel(GetProviderConversionUseCase(port, clock), sessions, clock, saved)
            .also { store.put("conversion", it) }
    private fun settle() = dispatcher.scheduler.advanceUntilIdle()
    @Test fun `draft errors preserve ready applied query and never invoke a port`() {
        val vm = vm(); vm.open(); settle()
        val ready = vm.uiState.value; val applied = vm.query
        listOf("" to "2026-09-30", "2026-9-01" to "2026-09-30", "2026-02-30" to "2026-09-30",
            "2026-09-30" to "2026-09-01", "2026-09-01" to "2026-10-05", "2025-09-01" to "2026-09-30")
            .forEach { (from, to) ->
                vm.editDates(from, to); vm.applyDates(); settle()
                assertNotNull(vm.filters.value.dateError); assertEquals(applied, vm.query); assertEquals(ready, vm.uiState.value)
            }
        assertEquals(1, repository.queries.size)
    }
    @Test fun `civil inclusive dates use Buenos Aires next midnight and today clamps to precise now`() {
        val vm = vm(); vm.open(); settle()
        vm.editDates("2026-09-01", "2026-09-30"); vm.applyDates(); settle()
        assertEquals(Instant.parse("2026-09-01T03:00:00Z"), vm.query.from!!.toInstant())
        assertEquals(Instant.parse("2026-10-01T03:00:00Z"), vm.query.to!!.toInstant())
        vm.editDates("2026-10-04", "2026-10-04"); vm.applyDates(); settle()
        assertEquals(Instant.parse("2026-10-04T03:00:00Z"), vm.query.from!!.toInstant())
        assertEquals(clock.instant(), vm.query.to!!.toInstant())
        vm.editDates("2025-10-04", "2026-10-03"); vm.applyDates(); settle()
        assertNull(vm.filters.value.dateError)
        assertEquals(Duration.ofDays(365), Duration.between(vm.query.from, vm.query.to))
    }
    @Test fun `default rolling period is captured on first entry rather than construction`() {
        val later = clock.instant().plusSeconds(3600)
        var now = clock.instant()
        val changing = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId) = this
            override fun instant() = now
        }
        val vm = ProviderConversionViewModel(GetProviderConversionUseCase(repository, changing), sessions, changing)
            .also { store.put("conversion", it) }
        settle(); assertTrue(repository.queries.isEmpty()); now = later; vm.open(); settle()
        assertEquals(later, vm.query.to!!.toInstant())
    }
    @Test fun `saved state restores only metadata then reloads exact query and guards loading and stale reading writes`() {
        val saved = SavedStateHandle(); var vm = vm(saved = saved); vm.open(); settle()
        vm.editDates("2026-09-01", "2026-09-30"); vm.applyDates(); settle()
        vm.expandAdvances(true); vm.expandPeriod(true); vm.rememberReadingPosition(8, 17, vm.readingVersion)
        val query = vm.query; val version = vm.readingVersion
        vm.editDates("", "2026-09-30")
        assertTrue(saved.keys().all { saved.get<Any>(it) is String || saved.get<Any>(it) is Number || saved.get<Any>(it) is Boolean })
        val snapshot = saved.keys().associateWith { saved.get<Any>(it) }
        repository.gate = CompletableDeferred()
        vm = vm(saved = SavedStateHandle(snapshot)); vm.open(); dispatcher.scheduler.runCurrent()
        vm.rememberReadingPosition(0, 0, version)
        assertEquals(8, vm.readingIndex); assertEquals(17, vm.readingOffset)
        repository.gate!!.complete(Unit); settle()
        assertEquals(query, vm.query); assertEquals(query, repository.queries.last())
        assertTrue(vm.advancesExpanded.value); assertTrue(vm.periodExpanded.value); assertEquals("", vm.filters.value.fromDay)
        vm.editDates("2026-08-01", "2026-08-31"); vm.applyDates(); settle()
        vm.rememberReadingPosition(8, 17, version)
        assertEquals(0, vm.readingIndex); assertEquals(0, vm.readingOffset)
        sessions.clearSession(); settle()
        assertEquals(ConversionFilters(), vm.filters.value); assertEquals(ConversionQuery(), vm.query)
        assertFalse(vm.advancesExpanded.value); assertFalse(vm.periodExpanded.value)
    }
    @Test fun `new query cancels obsolete success and unauthorized without clearing current session`() {
        listOf(ConversionOutcome.Success(conversionFixture()), ConversionOutcome.Failure.Unauthorized).forEach { obsolete ->
            val gate = CompletableDeferred<Unit>(); var calls = 0; var cancelled = false
            val port = object : ProviderConversionRepository {
                override suspend fun getConversion(query: ConversionQuery): ConversionOutcome {
                    if (++calls == 1) {
                        try { gate.await() } catch (_: CancellationException) {
                            cancelled = true; withContext(NonCancellable) { gate.await() }
                        }
                        return obsolete
                    }
                    return ConversionOutcome.Success(conversionFixture(5, true))
                }
            }
            val vm = vm(port); vm.open(); dispatcher.scheduler.runCurrent()
            vm.editDates("2026-09-01", "2026-09-30"); vm.applyDates(); dispatcher.scheduler.runCurrent()
            assertTrue(cancelled); assertEquals(5L, (vm.uiState.value as ProviderConversionUiState.Ready).conversion.proposals.stages.issued)
            gate.complete(Unit); settle()
            assertNotNull(sessions.getSession()); assertEquals(5L, (vm.uiState.value as ProviderConversionUiState.Ready).conversion.proposals.stages.issued)
        }
    }
}
