package com.loresuelvo.serviceprovider.ui.screens.auth

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.auth.WelcomeCategoriesUiState
import com.loresuelvo.serviceprovider.ui.auth.WelcomeError
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM Compose proof for the recoverable signup-failure presentation.
 *
 * The ViewModel translates provider details to [WelcomeError.Authentication];
 * this test verifies that the stateless screen renders the localized safe
 * resource and leaves every authentication action available for retry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeScreenFailureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun recoverable_auth_error_is_localized_and_authentication_controls_stay_enabled() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                WelcomeScreen(
                    error = WelcomeError.Authentication,
                    categories = WelcomeCategoriesUiState.Error,
                )
            }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule
            .onNodeWithText(context.getString(R.string.welcome_auth_error))
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(context.getString(R.string.welcome_register))
            .assertIsEnabled()
        composeTestRule
            .onNodeWithText(context.getString(R.string.welcome_login))
            .assertIsEnabled()
        composeTestRule
            .onNodeWithText(context.getString(R.string.welcome_google))
            .assertIsEnabled()
    }
}
