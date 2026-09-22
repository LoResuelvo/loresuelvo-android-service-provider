package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderProfileScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renders_authenticated_provider_identity_and_initials_fallback() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(
                    state = ProviderProfileUiState.Ready(provider()),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("CG").assertIsDisplayed()
        composeTestRule.onNodeWithText("Carlos Gómez").assertIsDisplayed()
        composeTestRule.onNodeWithText("carlos@example.com").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Plomería").performScrollTo().assertIsDisplayed()
    }

    private fun provider() = CurrentAccount.Provider(
        id = 1,
        name = "Carlos",
        surname = "Gómez",
        email = "carlos@example.com",
        category = Category(4, "Plomería"),
        profilePhotoUrl = null,
    )
}
