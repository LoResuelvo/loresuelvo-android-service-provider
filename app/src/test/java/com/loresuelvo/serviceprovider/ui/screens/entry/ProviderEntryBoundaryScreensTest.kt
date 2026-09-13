package com.loresuelvo.serviceprovider.ui.screens.entry

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderEntryBoundaryScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun retryable_error_is_localized_and_retryable() {
        var retried = false

        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderEntryErrorScreen(onRetry = { retried = true })
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.provider_entry_error_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_entry_retry))
            .performClick()
        assertTrue(retried)
    }

    @Test
    fun account_mismatch_exposes_explicit_local_session_exit() {
        var returned = false

        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderAccountMismatchScreen(onReturnToWelcome = { returned = true })
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_entry_account_mismatch_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_entry_account_mismatch_action))
            .performClick()
        assertTrue(returned)
    }
}
