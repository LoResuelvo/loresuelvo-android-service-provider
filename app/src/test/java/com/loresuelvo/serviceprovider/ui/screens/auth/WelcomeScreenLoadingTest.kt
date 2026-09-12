package com.loresuelvo.serviceprovider.ui.screens.auth

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.auth.WelcomeCategoriesUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JVM Compose proof for the duplicate-auth loading boundary. A busy Welcome
 * screen announces its state through a localized semantic and disables every
 * authentication action, including the secondary top-bar login.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WelcomeScreenLoadingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loading_state_is_accessible_and_disables_every_authentication_control() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                WelcomeScreen(
                    loading = true,
                    categories = WelcomeCategoriesUiState.Error,
                )
            }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.welcome_auth_loading))
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(context.getString(R.string.welcome_login))
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithText(context.getString(R.string.welcome_register))
            .performScrollTo()
            .assertIsNotEnabled()
        composeTestRule
            .onNodeWithText(context.getString(R.string.welcome_google))
            .performScrollTo()
            .assertIsNotEnabled()
    }
}
