package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JobRequestDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun renders_the_available_request_data_and_read_only_action_affordances() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                JobRequestDetailScreen(
                    uiState = JobRequestDetailUiState.Ready(
                        JobRequest(7, "Ana Pérez", "Reparar pérdida", "En la cocina"),
                    ),
                    onClose = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_job_request_detail_title))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Ana Pérez").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reparar pérdida").assertIsDisplayed()
        composeTestRule.onNodeWithText("En la cocina").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_job_request_continue_conversation))
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_job_request_reject))
            .assertIsNotEnabled()
    }
}
