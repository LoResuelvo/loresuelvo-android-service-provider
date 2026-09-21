package com.loresuelvo.serviceprovider.acceptance.identity

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.screens.identity.OptionalIdentityVerificationScreen
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OptionalIdentityVerificationAcceptanceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun optional_step_is_localized_accessible_and_operable_on_device() {
        composeRule.setContent {
            LoresuelvoTheme { OptionalIdentityVerificationScreen() }
        }

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals("Tu cuenta ya está creada", context.getString(R.string.identity_optional_title))
        composeRule.onNodeWithText(context.getString(R.string.identity_optional_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.identity_optional_description)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.identity_verify_now))
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText(context.getString(R.string.identity_verify_later))
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
