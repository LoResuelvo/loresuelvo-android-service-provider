package com.loresuelvo.serviceprovider.ui.screens.auth

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.entry.ProviderLogoutUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeLogoutScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun authentication_in_flight_disables_external_retry_without_triggering_it() {
        var retries = 0
        compose.setContent { LoresuelvoTheme {
            WelcomeScreen(loading = true,
                logoutState = ProviderLogoutUiState(externalLogoutPending = true),
                logoutRetryEnabled = false, onRetryLogout = { retries += 1 })
        } }
        compose.onNodeWithText(context.getString(R.string.provider_logout_retry)).assertIsNotEnabled()
        assertEquals(0, retries)
    }

    @Test fun external_pending_feedback_offers_actual_retry_action() {
        var retries = 0
        compose.setContent { LoresuelvoTheme {
            WelcomeScreen(logoutState = ProviderLogoutUiState(externalLogoutPending = true),
                onRetryLogout = { retries += 1 })
        } }
        compose.onNodeWithText(context.getString(R.string.provider_logout_external_pending)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_logout_retry)).performClick()
        assertEquals(1, retries)
    }

    @Test fun durable_failure_shows_local_retry_and_disabled_sign_in() {
        compose.setContent { LoresuelvoTheme {
            WelcomeScreen(loading = true, logoutState = ProviderLogoutUiState(localRemovalPending = true))
        } }
        compose.onNodeWithText(context.getString(R.string.provider_logout_local_pending)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_logout_retry)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.welcome_login)).assertIsNotEnabled()
    }

    @Test fun processing_shows_feedback_without_retry() {
        compose.setContent { LoresuelvoTheme {
            WelcomeScreen(loading = true, logoutState = ProviderLogoutUiState(processing = true))
        } }
        compose.onNodeWithText(context.getString(R.string.provider_logout_processing)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.provider_logout_retry)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.welcome_google)).performScrollTo().assertIsNotEnabled()
    }
}
