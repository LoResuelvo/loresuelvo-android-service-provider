package com.loresuelvo.serviceprovider.ui.screens.profile

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** JVM semantics smoke for the stateless provider-profile destination. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompleteProviderProfileScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renders_a_localized_profile_heading_and_description() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                CompleteProviderProfileScreen()
            }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_profile_description))
            .assertIsDisplayed()
    }
}
