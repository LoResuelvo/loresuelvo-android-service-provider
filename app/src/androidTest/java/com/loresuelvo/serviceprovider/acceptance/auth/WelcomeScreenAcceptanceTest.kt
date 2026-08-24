package com.loresuelvo.serviceprovider.acceptance.auth

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Acceptance smoke for the Welcome screen (US-33).
 *
 * Mirrors the consumer app's `WelcomeScreenAcceptanceTest`: each
 * `@Test` covers one scenario from `provider-welcome.feature`. The
 * CI emulator boots `en-US`; we resolve strings through the activity
 * so assertions match the locale the screen actually renders under
 * (see `AGENTS.md` -> "Aceptación: Locale del CI").
 *
 * The acceptance test deliberately asserts only the parts of the
 * `.feature` that are observable in the UI (logo, badge, title,
 * subtitle, 3 steps, buttons, legal). The categories / loading /
 * error branches of the Welcome flow are covered by
 * [com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModelTest].
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WelcomeScreenAcceptanceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun localizedString(@StringRes resourceId: Int): String =
        composeTestRule.activity.getString(resourceId)

    @Test
    fun should_display_loresuelvo_branding() {
        composeTestRule
            .onNodeWithText(localizedString(R.string.brand_name))
            .assertIsDisplayed()
    }

    @Test
    fun should_display_value_proposition() {
        composeTestRule
            .onNodeWithText(localizedString(R.string.welcome_badge_verified))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(localizedString(R.string.welcome_title))
            .assertIsDisplayed()
    }

    @Test
    fun should_display_three_how_it_works_steps() {
        composeTestRule
            .onNodeWithText(localizedString(R.string.welcome_step1_title))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(localizedString(R.string.welcome_step2_title))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(localizedString(R.string.welcome_step3_title))
            .assertIsDisplayed()
    }

    @Test
    fun should_display_register_action() {
        composeTestRule
            .onNodeWithText(localizedString(R.string.welcome_register))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun should_display_login_action() {
        composeTestRule
            .onNodeWithText(localizedString(R.string.welcome_login))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun should_display_google_login_action() {
        composeTestRule
            .onNodeWithText(localizedString(R.string.welcome_google))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun should_display_legal_text() {
        composeTestRule
            .onNodeWithText(localizedString(R.string.welcome_legal))
            .assertIsDisplayed()
    }
}