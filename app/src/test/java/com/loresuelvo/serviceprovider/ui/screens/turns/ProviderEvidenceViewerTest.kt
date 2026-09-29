package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR")
class ProviderEvidenceViewerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun viewer_has_visible_close_action_and_keeps_image_name_accessible() {
        var closes = 0
        compose.setContent { LoresuelvoTheme {
            ProviderEvidenceViewer("invalid://photo", "second.jpg", { closes++ })
        } }

        compose.onNodeWithTag("provider_evidence_viewer").assertExists()
        compose.onNodeWithContentDescription("second.jpg").assertExists()
        compose.onNodeWithContentDescription("Cerrar fotografía").performClick()
        assertEquals(1, closes)
    }

    @Test fun failed_full_size_photo_can_retry_without_closing_viewer() {
        var retries = 0
        compose.setContent { LoresuelvoTheme {
            ProviderEvidenceViewer("invalid://photo", "second.jpg", {}, onRetry = { retries++ })
        } }
        compose.onNodeWithText("Reintentar foto").performClick()
        assertEquals(1, retries)
        compose.onNodeWithTag("provider_evidence_viewer").assertExists()
    }
}
