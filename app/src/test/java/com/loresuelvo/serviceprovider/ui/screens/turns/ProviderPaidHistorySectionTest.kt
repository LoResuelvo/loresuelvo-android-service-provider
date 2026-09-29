package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderReview
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR")
class ProviderPaidHistorySectionTest {
    @get:Rule val compose = createComposeRule()
    private val originalZone = TimeZone.getDefault()

    @Before fun setUp() { TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires")) }
    @After fun tearDown() { TimeZone.setDefault(originalZone) }

    @Test fun shows_local_payment_time_and_received_review() {
        compose.setContent { LoresuelvoTheme { ProviderPaidHistorySection(
            Instant.parse("2026-08-15T16:00:00Z").toEpochMilli(), WorkOrderReview(5, "Excellent")) } }
        compose.onNodeWithText("el 15 de agosto a las 13:00").assertExists()
        compose.onNodeWithText("Calificación: 5 de 5").assertExists()
        compose.onNodeWithText("Excellent").assertExists()
    }

    @Test fun missing_review_has_feedback_without_inventing_payment_time_or_rating() {
        compose.setContent { LoresuelvoTheme { ProviderPaidHistorySection(null, null) } }
        compose.onNodeWithText("Todavía no hay reseña.").assertExists()
        compose.onNodeWithText("Calificación: 5 de 5").assertDoesNotExist()
        compose.onNodeWithText("el 15 de agosto a las 13:00").assertDoesNotExist()
    }
}
