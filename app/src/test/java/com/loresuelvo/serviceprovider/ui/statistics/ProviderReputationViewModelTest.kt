package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.SavedStateHandle
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
            override suspend fun getReputation(cursor: String?): ReputationOutcome {
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
    private fun ready(vm: ProviderReputationViewModel) = vm.uiState.value as ProviderReputationUiState.Ready
    private fun settle() = dispatcher.scheduler.advanceUntilIdle()
    private fun continuation() = reputationFixture().copy(reviews = listOf(
        ReceivedReview(179, 4, ""), ReceivedReview(172, 5, "Next real comment")), nextCursor = null)

    @Test fun `continuation deduplicates rows updates global indicators and blocks duplicate terminal loads`() {
        val vm = create(); vm.open(); settle(); vm.rememberReadingPosition(9, 37)
        repository.gate = CompletableDeferred()
        repository.outcome = ReputationOutcome.Success(continuation().copy(calculatedAt = java.time.Instant.parse("2026-10-04T12:00:00Z"),
            averageRating = 4.64, reviewCount = 25, reviewedPaidOrders = 25, coveragePercentage = 83.33,
            ratingDistribution = reputationFixture().ratingDistribution.map { if (it.rating == 5) it.copy(count = 18) else it }))
        vm.loadMore(); dispatcher.scheduler.runCurrent(); vm.loadMore(); vm.retry()
        assertTrue(ready(vm).loading); assertEquals(2, repository.calls)
        repository.gate!!.complete(Unit); settle()
        assertEquals(listOf(184, 179, 172), ready(vm).reputation.reviews.map { it.workOrderId })
        assertEquals(25L, ready(vm).reputation.reviewCount)
        assertEquals(25L, ready(vm).reputation.ratingDistribution.sumOf { it.count })
        assertEquals(83.33, ready(vm).reputation.coveragePercentage!!, 0.0)
        assertEquals(java.time.Instant.parse("2026-10-04T12:00:00Z"), ready(vm).reputation.calculatedAt)
        assertEquals(9, vm.readingIndex); assertEquals(37, vm.readingOffset)
        vm.loadMore(); settle(); assertEquals(listOf(null, "opaque+/cursor="), repository.cursors)
    }
    @Test fun `continuation failure retains rows and retries identical cursor while invalid cursor restarts`() {
        val vm = create(); vm.open(); settle()
        repository.outcome = ReputationOutcome.Failure.Network
        vm.loadMore(); settle(); assertEquals(reputationFixture().reviews, ready(vm).reputation.reviews)
        assertEquals(ReputationOutcome.Failure.Network, ready(vm).failure)
        repository.outcome = ReputationOutcome.Success(continuation()); vm.retry(); settle()
        assertEquals(listOf(null, "opaque+/cursor=", "opaque+/cursor="), repository.cursors)
        vm.refresh(); settle(); vm.rememberReadingPosition(10, 9)
        repository.outcome = ReputationOutcome.Success(reputationFixture()); vm.refresh(); settle()
        repository.outcome = ReputationOutcome.Failure.InvalidQuery; vm.loadMore(); settle()
        assertTrue(ready(vm).restartRequired)
        repository.outcome = ReputationOutcome.Success(reputationFixture()); vm.retry(); settle()
        assertNull(repository.cursors.last()); assertEquals(reputationFixture().reviews, ready(vm).reputation.reviews)
        assertEquals(0, vm.readingIndex)
    }
    @Test fun `refresh drops prior pages resets reading only after success and preserves valid result on failure`() {
        val vm = create(); vm.open(); settle()
        repository.outcome = ReputationOutcome.Success(continuation()); vm.loadMore(); settle()
        vm.rememberReadingPosition(10, 4)
        repository.outcome = ReputationOutcome.Failure.Network; vm.refresh(); settle()
        assertEquals(listOf(184, 179, 172), ready(vm).reputation.reviews.map { it.workOrderId })
        assertEquals(10, vm.readingIndex)
        val updated = reputationFixture().copy(reviews = listOf(ReceivedReview(200, 5, "New actual review")))
        repository.outcome = ReputationOutcome.Success(updated); vm.retry(); settle()
        assertEquals(updated, ready(vm).reputation); assertEquals(0, vm.readingIndex)
        assertEquals(1L, ready(vm).readingVersion)
    }
    @Test fun `refresh cancels continuation and rejects non cooperative late result`() {
        val gate = CompletableDeferred<Unit>(); var cancelled = false
        val port = object : ProviderReputationRepository {
            override suspend fun getReputation(cursor: String?): ReputationOutcome {
                if (cursor != null) {
                    try { gate.await() } catch (_: CancellationException) {
                        cancelled = true; withContext(NonCancellable) { gate.await() }
                    }
                    return ReputationOutcome.Success(continuation())
                }
                return ReputationOutcome.Success(reputationFixture())
            }
        }
        val vm = create(port); vm.open(); settle(); vm.loadMore(); dispatcher.scheduler.runCurrent()
        vm.refresh(); dispatcher.scheduler.runCurrent(); assertTrue(cancelled)
        gate.complete(Unit); settle(); assertEquals(reputationFixture(), ready(vm).reputation)
    }
    @Test fun `process restoration replays actual pages and saves no private payload or cursor`() {
        val saved = SavedStateHandle(); var responses = 0
        val port = object : ProviderReputationRepository {
            override suspend fun getReputation(cursor: String?): ReputationOutcome {
                responses++
                return ReputationOutcome.Success(if (cursor == null) reputationFixture() else continuation())
            }
        }
        fun restored() = ProviderReputationViewModel(GetProviderReputationUseCase(port), sessions, saved)
            .also { store.put("reputation", it) }
        val first = restored(); first.open(); settle(); first.loadMore(); settle(); first.rememberReadingPosition(10, 43)
        assertEquals(setOf("reputation.pages", "reputation.index", "reputation.offset"), saved.keys())
        val second = restored(); second.open(); settle()
        assertEquals(4, responses); assertEquals(10, second.readingIndex); assertEquals(43, second.readingOffset)
        assertFalse(ready(second).restoring); assertEquals(listOf(184, 179, 172), ready(second).reputation.reviews.map { it.workOrderId })
    }
    @Test fun `restoration failure preserves target metadata and retry resumes replay until terminal cursor`() {
        val saved = SavedStateHandle(mapOf("reputation.pages" to 3, "reputation.index" to 40, "reputation.offset" to 19))
        var failed = true; val cursors = mutableListOf<String?>()
        val port = object : ProviderReputationRepository {
            override suspend fun getReputation(cursor: String?): ReputationOutcome {
                cursors += cursor
                return if (cursor != null && failed) ReputationOutcome.Failure.Network
                    else ReputationOutcome.Success(if (cursor == null) reputationFixture() else continuation())
            }
        }
        val vm = ProviderReputationViewModel(GetProviderReputationUseCase(port), sessions, saved).also { store.put("reputation", it) }
        vm.open(); settle(); assertTrue(ready(vm).restoring); assertFalse(ready(vm).loading)
        assertEquals(40, vm.readingIndex); assertEquals(3, saved.get<Int>("reputation.pages"))
        failed = false; vm.retry(); settle()
        assertFalse(ready(vm).restoring); assertEquals(2, saved.get<Int>("reputation.pages"))
        assertEquals(listOf(null, "opaque+/cursor=", "opaque+/cursor="), cursors)
    }

    @Test fun `unauthorized continuation erases rows and reading metadata before further requests`() {
        val saved = SavedStateHandle()
        val vm = ProviderReputationViewModel(GetProviderReputationUseCase(repository), sessions, saved).also { store.put("reputation", it) }
        vm.open(); settle(); vm.rememberReadingPosition(19, 12)
        repository.outcome = ReputationOutcome.Failure.Unauthorized; vm.loadMore(); settle()
        assertNull(sessions.getSession()); assertEquals(ProviderReputationUiState.SessionExpired, vm.uiState.value)
        assertEquals(0, vm.readingIndex); assertEquals(0, vm.readingOffset); assertEquals(1, saved.get<Int>("reputation.pages"))
        vm.loadMore(); vm.retry(); vm.refresh(); settle(); assertEquals(2, repository.calls)
    }

}
