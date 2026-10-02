package com.loresuelvo.serviceprovider.ui.screens.profile

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CalendarConnectionStatus
import com.loresuelvo.serviceprovider.ui.profile.CalendarFeedback
import com.loresuelvo.serviceprovider.ui.profile.ProfileCalendarUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileCalendarCardTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun status_and_authorization_action_follow_platform_status() {
        val status = mutableStateOf(CalendarConnectionStatus.Disconnected)
        var clicks = 0
        compose.setContent {
            LoresuelvoTheme { ProfileCalendarCard(status.value, ProfileCalendarUiState(), { clicks++ }, {}, {}) }
        }
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_disconnected)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_connect)).performClick()
        assertEquals(1, clicks)
        compose.runOnIdle { status.value = CalendarConnectionStatus.ActionRequired }
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_action_required)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_reauthorize)).performClick()
        assertEquals(2, clicks)
        compose.runOnIdle { status.value = CalendarConnectionStatus.Connected }
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_connected)).assertIsDisplayed()
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).assertDoesNotExist()
    }

    @Test
    fun busy_state_announces_progress_and_disables_duplicate_action() {
        compose.setContent {
            LoresuelvoTheme { ProfileCalendarCard(CalendarConnectionStatus.Disconnected,
                ProfileCalendarUiState(loading = true), {}, {}, {}) }
        }
        compose.onNodeWithTag(PROFILE_CALENDAR_LOADING_TAG).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_loading)).assertIsDisplayed()
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).assertIsNotEnabled()
    }

    @Test
    fun confirmation_failure_offers_query_without_authorization() {
        var consents = 0
        var queries = 0
        compose.setContent {
            LoresuelvoTheme { ProfileCalendarCard(CalendarConnectionStatus.Disconnected,
                ProfileCalendarUiState(feedback = CalendarFeedback.ConfirmationFailed, confirmationRetry = true),
                { consents++ }, { queries++ }, {}) }
        }
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_confirmation_failed)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_retry_query)).performClick()
        assertEquals(1, queries)
        assertEquals(0, consents)
    }

    @Test
    fun unknown_status_offers_reload_and_never_authorizes() {
        var consents = 0
        var reloads = 0
        compose.setContent {
            LoresuelvoTheme { ProfileCalendarCard(CalendarConnectionStatus.Unavailable, ProfileCalendarUiState(),
                { consents++ }, {}, { reloads++ }) }
        }
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_unavailable)).assertIsDisplayed()
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).performClick()
        assertEquals(1, reloads)
        assertEquals(0, consents)
    }

    @Test
    fun rejected_code_offers_a_fresh_consent() {
        var consents = 0
        compose.setContent {
            LoresuelvoTheme { ProfileCalendarCard(CalendarConnectionStatus.Disconnected,
                ProfileCalendarUiState(feedback = CalendarFeedback.CodeRejected), { consents++ }, {}, {}) }
        }
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_code_rejected)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_profile_calendar_restart)).performClick()
        assertEquals(1, consents)
    }
}
