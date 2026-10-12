package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JobRequestListScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val request = JobRequest(41, "María López", "Plomería", "Reparar una pérdida de agua")

    @Test fun list_opens_the_selected_request_and_reflects_its_removal() {
        val state = mutableStateOf<ActivitySectionState<JobRequest>>(ActivitySectionState.Ready(listOf(request)))
        var selected: JobRequest? = null
        var backs = 0
        compose.setContent { LoresuelvoTheme {
            JobRequestListScreen(state.value, false, { backs++ }, {}, { selected = it })
        } }
        compose.onNodeWithText(request.consumerName).assertIsDisplayed()
        compose.onNodeWithTag("request_details_41").performClick()
        assertEquals(request, selected)
        compose.runOnIdle { state.value = ActivitySectionState.Ready(emptyList()) }
        compose.onNodeWithTag("request_41").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.provider_home_requests_empty)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.ui_back)).performClick()
        assertEquals(1, backs)
    }

    @Test fun failed_refresh_retains_requests_and_offers_retry() {
        var retries = 0
        compose.setContent { LoresuelvoTheme {
            JobRequestListScreen(ActivitySectionState.Ready(listOf(request), refreshFailed = true),
                false, {}, { retries++ }, {})
        } }
        compose.onNodeWithText(context.getString(R.string.provider_home_section_error)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_home_retry)).performClick()
        assertEquals(1, retries)
        compose.onNodeWithTag("request_details_41").assertExists()
    }

    @Test fun initial_failure_does_not_claim_there_are_no_requests() {
        compose.setContent { LoresuelvoTheme {
            JobRequestListScreen(ActivitySectionState.Error, false, {}, {}, {})
        } }
        compose.onNodeWithText(context.getString(R.string.provider_home_requests_empty)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.provider_home_retry)).assertIsDisplayed()
    }
}
