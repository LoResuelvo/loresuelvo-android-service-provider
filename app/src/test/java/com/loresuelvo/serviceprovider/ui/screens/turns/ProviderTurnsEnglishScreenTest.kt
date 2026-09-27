package com.loresuelvo.serviceprovider.ui.screens.turns

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class ProviderTurnsEnglishScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun english_normal_copy_and_actions() = checkEnglish(1f)
    @Test fun english_enlarged_copy_and_actions() = checkEnglish(1.5f)

    private fun checkEnglish(scale: Float) {
            val order = WorkOrder(7, "Ana Perez", "Repair the tap", 1, WorkOrderStatus.Scheduled)
            compose.setContent {
                val config = Configuration(LocalConfiguration.current).apply { fontScale = scale }
                CompositionLocalProvider(LocalConfiguration provides config) {
                    LoresuelvoTheme {
                        ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), {}, {})
                    }
                }
            }
            compose.onNodeWithText("Appointments").assertExists()
            compose.onNodeWithText("Confirmed").assertExists()
            compose.onNodeWithTag("provider_turn_details_7").performClick()
            compose.onNodeWithText("Appointment details").assertExists()
            compose.onNodeWithText("View conversation").assertExists()
    }
}
