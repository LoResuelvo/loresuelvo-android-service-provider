package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestImage
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
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
    fun renders_the_available_request_data_and_accept_action_affordances() {
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
            .assertIsEnabled()
    }

    @Test
    fun keeps_request_data_and_exposes_retry_after_accept_error() {
        var retried = false
        composeTestRule.setContent {
            LoresuelvoTheme {
                JobRequestDetailScreen(
                    uiState = JobRequestDetailUiState.AcceptError(
                        JobRequest(7, "Ana Pérez", "Reparar pérdida", "En la cocina"),
                    ),
                    onClose = {},
                    onRetry = {},
                    onRetryAccept = { retried = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Ana Pérez").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_job_request_accept_error))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_job_request_detail_retry))
            .performClick()

        assertTrue(retried)
    }

    @Test
    fun disables_acceptance_while_accepting() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                JobRequestDetailScreen(
                    uiState = JobRequestDetailUiState.Accepting(
                        JobRequest(7, "Ana Pérez", "Reparar pérdida", "En la cocina"),
                    ),
                    onClose = {},
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_job_request_continue_conversation))
            .assertIsNotEnabled()
    }

    @Test
    fun shows_unavailable_message_after_a_stale_acceptance() {
        var closed = false
        composeTestRule.setContent {
            LoresuelvoTheme {
                JobRequestDetailScreen(
                    uiState = JobRequestDetailUiState.AcceptUnavailable(
                        JobRequest(7, "Ana Pérez", "Reparar pérdida", "En la cocina"),
                    ),
                    onClose = {},
                    onUnavailable = { closed = true },
                    onRetry = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_job_request_detail_unavailable))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_job_request_detail_close))
            .performClick()

        assertTrue(closed)
    }

    @Test
    fun renders_context_image_and_opens_full_screen_viewer() {
        val imageDescription = context.getString(
            R.string.provider_job_request_image_description,
            "pileta.jpg",
        )
        composeTestRule.setContent {
            var selectedImageIndex by remember { mutableStateOf<Int?>(null) }
            LoresuelvoTheme {
                JobRequestDetailScreen(
                    uiState = JobRequestDetailUiState.Ready(
                        JobRequest(
                            7,
                            "Ana Pérez",
                            "Reparar pérdida",
                            "En la cocina",
                            images = listOf(
                                JobRequestImage("image-1", "https://cdn.example/image-1", "pileta.jpg"),
                            ),
                        ),
                    ),
                    onClose = {},
                    onRetry = {},
                    selectedImageIndex = selectedImageIndex,
                    onImageSelected = { selectedImageIndex = it },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(imageDescription)
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.provider_job_request_image_viewer_close))
            .assertIsDisplayed()
    }
}
