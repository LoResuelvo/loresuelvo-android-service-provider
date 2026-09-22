package com.loresuelvo.serviceprovider.ui.components.bottomnav

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class LoresuelvoBottomBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renders_home_and_messages_tabs_on_a_primary_destination() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                LoresuelvoBottomBar(
                    currentRoute = BottomDestination.Home.route,
                    onNavigate = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(PROVIDER_BOTTOM_BAR_TAG).assertExists()
        BottomDestination.all.forEach { destination ->
            composeTestRule
                .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + destination.route)
                .assertExists()
        }
    }

    @Test
    fun hides_the_bar_on_non_primary_destinations() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                LoresuelvoBottomBar(
                    currentRoute = "welcome",
                    onNavigate = {},
                )
            }
        }
        composeTestRule.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_TAG).assertCountEquals(0)
    }

    @Test
    fun hides_the_bar_when_current_route_is_null() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                LoresuelvoBottomBar(
                    currentRoute = null,
                    onNavigate = {},
                )
            }
        }
        composeTestRule.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_TAG).assertCountEquals(0)
    }

    @Test
    fun clicking_a_tab_invokes_onNavigate_with_its_destination() {
        val captured = mutableListOf<BottomDestination>()
        composeTestRule.setContent {
            LoresuelvoTheme {
                LoresuelvoBottomBar(
                    currentRoute = BottomDestination.Home.route,
                    onNavigate = { captured += it },
                )
            }
        }
        for (destination in BottomDestination.all) {
            captured.clear()
            composeTestRule
                .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + destination.route)
                .performClick()
            assertEquals(listOf(destination), captured)
        }
    }

    @Test
    fun exposes_the_selected_tab_to_accessibility_services() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                LoresuelvoBottomBar(
                    currentRoute = BottomDestination.Profile.route,
                    onNavigate = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path)
            .assertIsSelected()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Home.path)
            .assertIsNotSelected()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path)
            .assertIsNotSelected()
    }
}
