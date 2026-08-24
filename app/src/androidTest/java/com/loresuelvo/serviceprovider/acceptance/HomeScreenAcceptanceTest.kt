package com.loresuelvo.serviceprovider.acceptance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.loresuelvo.serviceprovider.ui.screens.home.HomeScreen
import org.junit.Rule
import org.junit.Test

/**
 * Acceptance smoke for the placeholder HomeScreen (Fase 0).
 *
 * Uses `createComposeRule()` (not `createAndroidComposeRule<MainActivity>()`)
 * because the setup deliberately does NOT wire Hilt yet — that lands in
 * Fase 1 alongside the walking skeleton.
 */
class HomeScreenAcceptanceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun should_render_title_and_greeting() {
        composeTestRule.setContent {
            HomeScreen(
                title = "LoResuelvo — Prestador",
                greeting = "Hola, prestador",
            )
        }

        composeTestRule.onNodeWithText("LoResuelvo — Prestador").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hola, prestador").assertIsDisplayed()
    }
}
