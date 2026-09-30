package com.loresuelvo.serviceprovider.ui.screens.home

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListUiState
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderHomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun opens_turns_and_proposals_from_their_section_headers() {
        var turnsOpens = 0
        var proposalOpens = 0
        var conversationId: Int? = null
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderHomeScreen(
                    provider = provider(),
                    uiState = ProviderHomeUiState(
                        jobRequests = ActivitySectionState.Ready(emptyList()),
                        scheduledWork = ActivitySectionState.Ready(listOf(workOrder())),
                    ),
                    onRetryJobRequests = {},
                    onRetryScheduledWork = {},
                    onJobRequestClick = {},
                    onMercadoPagoClick = {},
                    onAllTurnsClick = { turnsOpens++ },
                    onAllProposalsClick = { proposalOpens++ },
                    onProposalConversation = { conversationId = it },
                    proposalsState = ServiceProposalListUiState(
                        proposals = listOf(proposal(3).copy(status = ServiceProposalStatus.Accepted),
                            proposal(1), proposal(2), proposal(4).copy(status = ServiceProposalStatus.Rejected)),
                        loading = false,
                    ),
                )
            }
        }

        composeTestRule.onNodeWithTag("home_proposals_row")
            .performScrollTo().performScrollToIndex(1)
        composeTestRule.onAllNodesWithText("2").assertCountEquals(1)
        composeTestRule.onNodeWithTag("home_proposal_details_2").performClick()
        composeTestRule.onNodeWithTag("proposal_detail_reason").assertIsDisplayed()
        composeTestRule.onNodeWithText("Proposal 2").assertIsDisplayed()
        assertEquals(0, proposalOpens)
        composeTestRule.onNodeWithText(context.getString(R.string.proposal_detail_conversation))
            .performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.runOnIdle { assertEquals(93, conversationId) }
        composeTestRule.onNodeWithTag("scheduled_view_all_turns")
            .performScrollTo().performClick()
        composeTestRule.onNodeWithTag("jobs_view_all_proposals")
            .performScrollTo().performClick()

        assertEquals(1, turnsOpens)
        assertEquals(1, proposalOpens)
    }

    @Test @Config(qualifiers = "en")
    fun english_scheduled_section_uses_english_heading_action_and_link() {
        composeTestRule.setContent { LoresuelvoTheme {
            ProviderHomeScreen(provider(), ProviderHomeUiState(
                jobRequests = ActivitySectionState.Ready(emptyList()),
                scheduledWork = ActivitySectionState.Ready(listOf(workOrder())),
            ), {}, {}, {}, {})
        } }
        composeTestRule.onNodeWithTag("scheduled_view_all_turns").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("My jobs").assertCountEquals(1)
        assertEquals("Scheduled work", context.getString(R.string.provider_home_scheduled_count_label))
        assertEquals("Scheduled work", context.getString(R.string.provider_home_scheduled_action))
        assertEquals("View all", context.getString(R.string.provider_turns_view_all))
    }

    @Test @Config(qualifiers = "en")
    fun enlarged_scheduled_header_keeps_count_and_view_all_separate() {
        composeTestRule.setContent {
            val enlarged = Configuration(LocalConfiguration.current).apply { fontScale = 1.5f }
            CompositionLocalProvider(LocalConfiguration provides enlarged) {
                LoresuelvoTheme { ProviderHomeScreen(provider(), ProviderHomeUiState(
                    jobRequests = ActivitySectionState.Ready(emptyList()),
                    scheduledWork = ActivitySectionState.Ready(listOf(workOrder())),
                ), {}, {}, {}, {}) }
            }
        }
        composeTestRule.onNodeWithTag("scheduled_view_all_turns").performScrollTo()
        val title = composeTestRule.onNodeWithTag("scheduled_section_title").fetchSemanticsNode().boundsInRoot
        val count = composeTestRule.onNodeWithTag("scheduled_section_count").fetchSemanticsNode().boundsInRoot
        val link = composeTestRule.onNodeWithTag("scheduled_view_all_turns").fetchSemanticsNode().boundsInRoot
        assertTrue(title.right < count.left)
        assertTrue(link.top >= maxOf(title.bottom, count.bottom))
    }

    @Test
    fun home_turn_card_details_selects_order_while_view_all_opens_list() {
        var selected: WorkOrder? = null
        var listOpens = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderHomeScreen(provider(), ProviderHomeUiState(
                    jobRequests = ActivitySectionState.Ready(emptyList()),
                    scheduledWork = ActivitySectionState.Ready(listOf(workOrder())),
                ), {}, {}, {}, {}, onAllTurnsClick = { listOpens++ },
                    onTurnDetailsClick = { selected = it })
            }
        }
        composeTestRule.onNodeWithTag("provider_turn_details_${workOrder().id}").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.runOnIdle { assertEquals(workOrder(), selected) }
        assertEquals(0, listOpens)
        composeTestRule.onNodeWithTag("scheduled_view_all_turns").performScrollTo().performClick()
        assertEquals(1, listOpens)
    }

    @Test
    fun renders_provider_identity_photo_fallback_activity_and_real_counts() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderHomeScreen(
                    provider = provider(),
                    uiState = ProviderHomeUiState(
                        jobRequests = ActivitySectionState.Ready(listOf(jobRequest())),
                        scheduledWork = ActivitySectionState.Ready(listOf(workOrder())),
                    ),
                    onRetryJobRequests = {},
                    onRetryScheduledWork = {},
                    onJobRequestClick = {},
                    onMercadoPagoClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.provider_home_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText("CG").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carlos Gómez").assertIsDisplayed()
        composeTestRule.onNodeWithText("Plomería").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reparar pérdida").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Ana Pérez").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Pérdida debajo de la pileta").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("1").assertCountEquals(2)
    }

    @Test
    fun renders_empty_sections_with_zero_counts() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderHomeScreen(
                    provider = provider(),
                    uiState = ProviderHomeUiState(
                        jobRequests = ActivitySectionState.Ready(emptyList()),
                        scheduledWork = ActivitySectionState.Ready(emptyList()),
                    ),
                    onRetryJobRequests = {},
                    onRetryScheduledWork = {},
                    onJobRequestClick = {},
                    onMercadoPagoClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_home_requests_empty))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_home_scheduled_empty))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithText("0").assertCountEquals(2)
    }

    @Test
    fun exposes_retry_for_a_failed_section_without_placeholder_actions() {
        var retried = false

        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderHomeScreen(
                    provider = provider(),
                    uiState = ProviderHomeUiState(
                        jobRequests = ActivitySectionState.Error,
                        scheduledWork = ActivitySectionState.Ready(emptyList()),
                    ),
                    onRetryJobRequests = { retried = true },
                    onRetryScheduledWork = {},
                    onJobRequestClick = {},
                    onMercadoPagoClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_home_section_error))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_home_retry))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("provider-home-requests-action").assertDoesNotExist()
        composeTestRule.onNodeWithTag("provider-home-scheduled-action").assertDoesNotExist()
        assertTrue(!retried)
    }

    @Test
    fun exposes_a_view_request_action_for_each_pending_request() {
        var selected: JobRequest? = null
        val request = jobRequest()

        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderHomeScreen(
                    provider = provider(),
                    uiState = ProviderHomeUiState(
                        jobRequests = ActivitySectionState.Ready(listOf(request)),
                        scheduledWork = ActivitySectionState.Ready(emptyList()),
                    ),
                    onRetryJobRequests = {},
                    onRetryScheduledWork = {},
                    onJobRequestClick = { selected = it },
                    onMercadoPagoClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_home_view_request))
            .performScrollTo()
            .performClick()

        assertEquals(request, selected)
    }

    private fun proposal(id: Int) = ServiceProposalSummary(
        id, 93, 1500050, Instant.parse("2026-10-06T12:00:00Z").toEpochMilli(), "Proposal $id", 45,
        ServiceProposalStatus.Pending, Instant.parse("2026-09-21T12:00:00Z").toEpochMilli(),
        ServiceProposalCounterpart(7, "consumer", "Ana", "Pérez", null, null),
        ServiceProposalBookingTerms(
            "ARS", 1500050, 1000, 1499050, 500, 100, 400, 1100, 1499450, 1500550,
            Instant.parse("2026-09-30T12:00:00Z").toEpochMilli(),
        ),
    )

    private fun provider() = CurrentAccount.Provider(
        id = 1,
        name = "Carlos",
        surname = "Gómez",
        email = "carlos@example.com",
        category = Category(4, "Plomería"),
        profilePhotoUrl = null,
    )

    private fun jobRequest() = JobRequest(
        id = 10,
        consumerName = "Ana Pérez",
        title = "Reparar pérdida",
        description = "Pérdida debajo de la pileta",
    )

    private fun workOrder() = WorkOrder(
        id = 20,
        consumerName = "Ana Pérez",
        description = "Pérdida debajo de la pileta",
        scheduledOn = Instant.parse("2026-09-20T15:00:00Z").toEpochMilli(),
        status = WorkOrderStatus.Scheduled,
    )
}
