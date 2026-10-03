package com.loresuelvo.serviceprovider.ui.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
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

    @Test fun `invalid dates retain valid results and never query the repository`() {
        repository.respondToQuery = true
        val vm = create(); dispatcher.scheduler.advanceUntilIdle()
        val original = vm.uiState.value
        listOf(Triple("2026-09-20", "2026-09-10", ActivityDateError.REVERSED),
            Triple("2025-01-01", "2026-09-30", ActivityDateError.TOO_LONG),
            Triple("2026-09-01", "2026-10-04", ActivityDateError.FUTURE),
            Triple("2026-02-30", "2026-09-30", ActivityDateError.FORMAT),
            Triple("", "2026-09-30", ActivityDateError.FORMAT)).forEach { (from, through, error) ->
            vm.editDates(from, through); vm.applyDates(); dispatcher.scheduler.advanceUntilIdle()
            assertEquals(error, vm.filters.value.dateError); assertEquals(original, vm.uiState.value)
        }
        assertEquals(1, repository.queries.size)
    }

    @Test fun `inclusive calendar dates support exact 365 days and today up to now`() {
        repository.respondToQuery = true
        val vm = create(); dispatcher.scheduler.advanceUntilIdle()
        vm.editDates("2025-10-01", "2026-09-30"); vm.applyDates(); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.filters.value.dateError)
        assertEquals(java.time.Duration.ofDays(365), java.time.Duration.between(vm.query.from, vm.query.to))
        vm.editDates("2026-10-03", "2026-10-03"); vm.applyDates(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(Instant.parse("2026-10-03T03:00:00Z"), vm.query.from)
        assertEquals(Instant.parse("2026-10-03T12:00:00Z"), vm.query.to)
    }

    @Test fun `grouping and comparison preserve exact query for retry and can be disabled`() {
        repository.respondToQuery = true
        val vm = create(); dispatcher.scheduler.advanceUntilIdle()
        vm.selectGranularity(ActivityGranularity.WEEK); dispatcher.scheduler.advanceUntilIdle()
        vm.comparePrevious(true); dispatcher.scheduler.advanceUntilIdle()
        assertNotNull((vm.uiState.value as ProviderActivityUiState.Ready).activity.comparison)
        val query = vm.query
        repository.respondToQuery = false; repository.outcome = ActivityOutcome.Failure.Network
        vm.retry(); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value is ProviderActivityUiState.Error)
        repository.respondToQuery = true; vm.retry(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(query, repository.queries.last())
        vm.comparePrevious(false); dispatcher.scheduler.advanceUntilIdle()
        assertNull((vm.uiState.value as ProviderActivityUiState.Ready).activity.comparison)
        assertEquals(ActivityGranularity.WEEK, vm.query.granularity)
    }

    @Test fun `superseded non cooperative filter response cannot replace the current results`() {
        val gates = mutableListOf<CompletableDeferred<Unit>>()
        val queries = mutableListOf<ActivityQuery>()
        val stubborn = object : ProviderActivityRepository {
            override suspend fun getActivity(query: ActivityQuery): ActivityOutcome {
                queries += query
                val gate = CompletableDeferred<Unit>().also { gates += it }
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { gate.await() }
                return ActivityOutcome.Success(activityForQuery(query))
            }
        }
        val vm = ProviderActivityViewModel(GetProviderActivityUseCase(stubborn), sessions,
            Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)).also { viewModelStore.put("activity", it) }
        dispatcher.scheduler.runCurrent()
        vm.selectGranularity(ActivityGranularity.MONTH); dispatcher.scheduler.runCurrent()
        assertEquals(2, queries.size)
        gates[1].complete(Unit); dispatcher.scheduler.runCurrent()
        val latest = vm.uiState.value
        gates[0].complete(Unit); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(latest, vm.uiState.value)
        assertEquals("month", (latest as ProviderActivityUiState.Ready).activity.period.granularity)
    }

    @Test fun `weekly and monthly responses preserve Buenos Aires calendar boundaries including zeros`() {
        repository.respondToQuery = true
        val vm = create(); dispatcher.scheduler.advanceUntilIdle()
        vm.editDates("2026-07-01", "2026-09-30"); vm.applyDates(); dispatcher.scheduler.advanceUntilIdle()
        ActivityGranularity.entries.forEach { grouping ->
            vm.selectGranularity(grouping); dispatcher.scheduler.advanceUntilIdle()
            val buckets = (vm.uiState.value as ProviderActivityUiState.Ready).activity.evolution
            assertEquals(vm.query.from, buckets.first().from); assertEquals(vm.query.to, buckets.last().to)
            assertTrue(buckets.drop(1).all { it.confirmedBookings == 0L })
            buckets.drop(1).forEach { bucket ->
                val start = bucket.from.atZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                assertEquals(java.time.LocalTime.MIDNIGHT, start.toLocalTime())
                if (grouping == ActivityGranularity.WEEK) assertEquals(java.time.DayOfWeek.MONDAY, start.dayOfWeek)
                if (grouping == ActivityGranularity.MONTH) assertEquals(1, start.dayOfMonth)
            }
        }
    }
}
