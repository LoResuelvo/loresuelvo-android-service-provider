package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderCompletionImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderCompletionReport
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR")
class ProviderCompletionEvidenceSectionTest {
    @get:Rule val compose = createComposeRule()
    private val originalZone = TimeZone.getDefault()

    @Before fun setUp() { TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires")) }
    @After fun tearDown() { TimeZone.setDefault(originalZone) }

    @Test fun shows_report_and_three_photos_in_received_order() {
        val report = WorkOrderCompletionReport(17, "Installed the new valve",
            Instant.parse("2026-08-15T16:00:00Z").toEpochMilli(),
            listOf("first", "second", "third").map { id ->
                WorkOrderCompletionImage(id, "$id.jpg", "invalid://$id")
            })
        compose.setContent { LoresuelvoTheme { ProviderCompletionEvidenceSection(report) } }

        compose.onNodeWithText("Evidencia de finalización").assertExists()
        compose.onNodeWithText("Installed the new valve").assertExists()
        compose.onNodeWithText("el 15 de agosto a las 13:00").assertExists()
        compose.onNodeWithTag("provider_evidence_photo_1").assertExists()
        compose.onNodeWithTag("provider_evidence_photo_2").assertExists()
        compose.onNodeWithTag("provider_evidence_photo_3").assertExists()
    }

    @Test fun missing_report_has_feedback() {
        compose.setContent { LoresuelvoTheme { ProviderCompletionEvidenceSection(null) } }
        compose.onNodeWithText("La evidencia todavía no está disponible.").assertExists()
    }

    @Test fun report_without_photos_keeps_description_and_shows_photo_feedback() {
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionEvidenceSection(WorkOrderCompletionReport(17, "Done", null, emptyList()))
        } }
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("No hay fotografías de la entrega.").assertExists()
    }

    @Test fun selecting_second_thumbnail_emits_its_file_id() {
        var selected: String? = null
        val report = WorkOrderCompletionReport(17, "Done", 1000,
            listOf("first", "second", "third").map { WorkOrderCompletionImage(it, "$it.jpg", "invalid://$it") })
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionEvidenceSection(report, onPhotoClick = { selected = it })
        } }

        compose.onNodeWithTag("provider_evidence_photo_2").performClick()
        assertEquals("second", selected)
    }

    @Test fun failed_thumbnail_retries_only_its_file_id() {
        var retried: String? = null
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionEvidenceSection(WorkOrderCompletionReport(17, "Done", null,
                listOf(WorkOrderCompletionImage("file-2", "two.jpg", "invalid://photo"))),
                onPhotoRetry = { retried = it })
        } }
        compose.onNodeWithText("Reintentar foto").performClick()
        assertEquals("file-2", retried)
    }
}
