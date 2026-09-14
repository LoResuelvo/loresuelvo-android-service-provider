package com.loresuelvo.serviceprovider.ui.screens.messages

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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderMessagesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun renders_the_messages_destination_shell() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderMessagesScreen()
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_messages_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_messages_placeholder))
            .assertIsDisplayed()
    }
}
