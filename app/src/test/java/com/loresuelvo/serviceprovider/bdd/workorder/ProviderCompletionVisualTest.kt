package com.loresuelvo.serviceprovider.bdd.workorder

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionDraftValidation
import com.loresuelvo.serviceprovider.ui.screens.turns.CompletionFormAvailability
import com.loresuelvo.serviceprovider.ui.screens.turns.ProviderCompletionFormScreen
import com.loresuelvo.serviceprovider.ui.screens.turns.ProviderTurnDetailScreen
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.ui.turns.CompletionEvidenceSelection
import com.loresuelvo.serviceprovider.ui.turns.CompletionSubmissionState
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionStatus
import com.loresuelvo.serviceprovider.ui.turns.EvidenceUploadStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ProviderCompletionVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [34], qualifiers = "es-rAR")
    fun spanishNormal() = checkFlow(1f)

    @Test @Config(sdk = [34], qualifiers = "es-rAR")
    fun spanishLarge() = checkFlow(1.5f)

    @Test @Config(sdk = [34], qualifiers = "en")
    fun englishNormal() = checkFlow(1f)

    @Test @Config(sdk = [34], qualifiers = "en")
    fun englishLarge() = checkFlow(1.5f)

    private fun checkFlow(fontScale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val order = WorkOrder(42, "Ana Pérez", "Repair the tap", 1_000, WorkOrderStatus.Scheduled)
        val photo = CompletionEvidenceSelection(1, "one", EvidenceSelectionStatus.Ready(
            PreparedEvidenceImage("one.jpg", "image/jpeg", 100, "/missing/one.jpg")),
            EvidenceUploadStatus.Confirmed("file-one"))
        var summary by mutableStateOf(true)
        var photos by mutableStateOf(false)
        var submission by mutableStateOf<CompletionSubmissionState>(CompletionSubmissionState.Idle)
        var validation by mutableStateOf<CompletionDraftValidation.Invalid?>(null)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                LoresuelvoTheme {
                    if (summary) ProviderTurnDetailScreen(order, {}, {}, onCompletion = { summary = false })
                    else ProviderCompletionFormScreen(order, CompletionFormAvailability.Eligible,
                        "Done", {}, { photos = true }, {}, canAddPhotos = true,
                        evidence = if (photos) listOf(photo) else emptyList(),
                        validationIssue = validation, canAttemptSubmit = true,
                        onSubmitAttempt = { validation = CompletionDraftValidation.Invalid.PhotoRequired },
                        submission = submission)
                }
            }
        }
        compose.onNodeWithTag("provider_turn_completion").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.provider_completion_description)).assertExists()
        compose.onNodeWithTag("completion_description").assertExists()
        compose.onNodeWithTag("completion_submit").performScrollTo().performClick()
        compose.onNodeWithTag("completion_validation_issue").performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.provider_completion_add_photos)).performClick()
        compose.onNodeWithTag("completion_evidence_0_1").performScrollTo().assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.provider_completion_photo_preview, 1)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.provider_completion_photo_remove_description, 1)).assertExists()
        compose.runOnIdle { submission = CompletionSubmissionState.Sending }
        compose.onNodeWithText(context.getString(R.string.provider_completion_report_sending)).assertExists()
        compose.runOnIdle { submission = CompletionSubmissionState.QueryFailed }
        compose.onNodeWithText(context.getString(R.string.provider_completion_report_query_failed)).assertExists()
        compose.onNodeWithTag("completion_retry_query").performScrollTo().assertExists()
        compose.runOnIdle { submission = CompletionSubmissionState.Confirmed(17, true) }
        compose.onNodeWithText(context.getString(R.string.provider_completion_report_success)).assertExists()
    }
}
