package com.loresuelvo.serviceprovider.ui.screens.identity

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OptionalIdentityVerificationScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun shows_created_account_and_optional_actions() {
        composeRule.setContent {
            LoresuelvoTheme { OptionalIdentityVerificationScreen() }
        }

        composeRule.onNodeWithText(context.getString(R.string.identity_optional_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.identity_optional_description)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.identity_verify_now)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.identity_verify_later)).assertIsDisplayed()
    }

    @Test
    fun later_action_is_forwarded() {
        var laterSelected = false
        composeRule.setContent {
            LoresuelvoTheme {
                OptionalIdentityVerificationScreen(onLater = { laterSelected = true })
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.identity_verify_later)).performClick()

        assertTrue(laterSelected)
    }

    @Test
    fun loading_is_announced_and_disables_both_actions() {
        composeRule.setContent {
            LoresuelvoTheme {
                OptionalIdentityVerificationScreen(
                    uiState = OptionalIdentityVerificationUiState(loading = true),
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.identity_starting_verification))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.identity_verify_now)).assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.identity_verify_later)).assertIsNotEnabled()
    }
}
