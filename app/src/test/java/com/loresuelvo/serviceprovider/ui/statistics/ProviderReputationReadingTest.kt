package com.loresuelvo.serviceprovider.ui.statistics

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderReputationUseCase
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderReputationRoute
import com.loresuelvo.serviceprovider.ui.screens.statistics.REPUTATION_READING_POSITION
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w390dp-h800dp-mdpi")
class ProviderReputationReadingTest {
    @get:Rule val compose = createComposeRule()
    private val sessions = ActivityTestSessionStore()
    private val saved = SavedStateHandle()
    private val store = ViewModelStore()
    @After fun close() { compose.runOnIdle { store.clear() } }
    private fun create(repository: ProviderReputationRepository): ProviderReputationViewModel {
        lateinit var vm: ProviderReputationViewModel
        compose.runOnIdle {
            vm = ProviderReputationViewModel(GetProviderReputationUseCase(repository), sessions, saved)
            store.put("reputation", vm)
        }
        return vm
    }
    private fun page(cursor: String?): ProviderReputation {
        val number = cursor?.toInt() ?: 0
        return reputationFixture().copy(reviewCount = 60, reviewedPaidOrders = 60, eligiblePaidOrders = 75,
            ratingDistribution = listOf(RatingCount(1, 0), RatingCount(2, 0), RatingCount(3, 0), RatingCount(4, 22), RatingCount(5, 38)),
            reviews = (0 until 20).map { ReceivedReview(300 - number * 20 - it, if (number * 20 + it < 38) 5 else 4, "Actual review") },
            nextCursor = if (number == 2) null else (number + 1).toString())
    }
    private fun position() = compose.onNodeWithTag("provider_reputation").fetchSemanticsNode().config[REPUTATION_READING_POSITION]

    @Test fun `refresh and logout metadata remain authoritative through real lazy layout changes`() {
        var refreshed = false
        val repository = object : ProviderReputationRepository {
            override suspend fun getReputation(cursor: String?) = ReputationOutcome.Success(
                if (refreshed) page(null).copy(reviews = listOf(ReceivedReview(400, 5, "New actual review")), nextCursor = null)
                else page(cursor))
        }
        val vm = create(repository)
        compose.setContent { LoresuelvoTheme { ProviderReputationRoute(vm) } }
        compose.waitForIdle()
        compose.runOnIdle { vm.loadMore() }; compose.waitForIdle()
        compose.onNodeWithTag("provider_reputation").performScrollToNode(hasText("Trabajo #275"))
        compose.waitForIdle(); assertTrue(vm.readingIndex > 20)
        compose.runOnIdle { refreshed = true; vm.refresh() }; compose.waitForIdle()
        assertEquals(0 to 0, position()); assertEquals(0, vm.readingIndex); assertEquals(0, vm.readingOffset)
        compose.onNodeWithTag("provider_reputation").performScrollToNode(hasText("Trabajo #400"))
        compose.runOnIdle { sessions.clearSession() }; compose.waitForIdle()
        assertEquals(ProviderReputationUiState.SessionExpired, vm.uiState.value)
        assertEquals(0, vm.readingIndex); assertEquals(0, vm.readingOffset)
        assertEquals(1, saved.get<Int>("reputation.pages"))
    }

    @Test fun `failed process replay does not clamp deep metadata and successful retry restores real pages`() {
        saved["reputation.pages"] = 3; saved["reputation.index"] = 40; saved["reputation.offset"] = 19
        var fail = true
        val cursors = mutableListOf<String?>()
        val repository = object : ProviderReputationRepository {
            override suspend fun getReputation(cursor: String?): ReputationOutcome {
                cursors += cursor
                return if (cursor == "1" && fail) ReputationOutcome.Failure.Network
                    else ReputationOutcome.Success(page(cursor))
            }
        }
        val vm = create(repository)
        compose.setContent { LoresuelvoTheme { ProviderReputationRoute(vm) } }
        compose.waitForIdle()
        assertEquals(40, vm.readingIndex); assertEquals(19, vm.readingOffset)
        assertEquals(3, saved.get<Int>("reputation.pages"))
        assertTrue((vm.uiState.value as ProviderReputationUiState.Ready).restoring)
        compose.onNodeWithTag("provider_reputation").performScrollToNode(hasText("Reintentar"))
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("Reintentar").performClick(); compose.waitForIdle()
        assertEquals(listOf(null, "1", "1", "2"), cursors)
        assertEquals(40 to 19, position()); assertEquals(40, vm.readingIndex); assertEquals(19, vm.readingOffset)
        assertFalse((vm.uiState.value as ProviderReputationUiState.Ready).restoring)
    }
}
