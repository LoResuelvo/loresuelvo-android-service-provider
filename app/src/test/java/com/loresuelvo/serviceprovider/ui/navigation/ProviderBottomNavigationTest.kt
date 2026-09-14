package com.loresuelvo.serviceprovider.ui.navigation

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderBottomNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun renders_home_and_messages_and_navigates_to_messages_once() {
        var selectedRoute: String? = null

        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderBottomBar(
                    currentRoute = Route.Home.path,
                    onNavigate = { selectedRoute = it.route },
                )
            }
        }

        composeTestRule.onNodeWithTag(PROVIDER_BOTTOM_BAR_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_bottom_nav_home))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_bottom_nav_messages))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Home.path)
            .assertIsSelected()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path)
            .performClick()

        assertEquals(Route.Messages.path, selectedRoute)
    }

    @Test
    fun only_top_level_home_and_messages_routes_show_the_bar() {
        assertTrue(ProviderBottomDestination.shouldShow(Route.Home.path))
        assertTrue(ProviderBottomDestination.shouldShow(Route.Messages.path))
        assertFalse(ProviderBottomDestination.shouldShow(Route.Welcome.path))
        assertFalse(ProviderBottomDestination.shouldShow(Route.Conversation.path))
    }
}
