package com.loresuelvo.serviceprovider.ui.screens.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
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
        composeTestRule.onAllNodesWithText("1").assertCountEquals(4)
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
        composeTestRule.onAllNodesWithText("0").assertCountEquals(4)
    }

    @Test
    fun exposes_retry_for_a_failed_section_and_keeps_future_detail_actions_disabled() {
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
        composeTestRule.onNodeWithTag("provider-home-requests-action").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("provider-home-scheduled-action").assertIsNotEnabled()
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
