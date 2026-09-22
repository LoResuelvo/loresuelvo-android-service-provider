package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.DateFormat
import java.util.Date

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

    @Test
    fun loading_hides_private_data_and_connection_actions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(state = ProviderProfileUiState.Loading, onBack = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_view_loading))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.mercadopago_connect_button))
            .assertDoesNotExist()
    }

    @Test
    fun account_failure_shows_retry_without_connection_action() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var retries = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(
                    state = ProviderProfileUiState.Unavailable,
                    onBack = {},
                    onRetry = { retries += 1 },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_view_unavailable))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_view_retry))
            .performClick()
        assertEquals(1, retries)
        composeTestRule.onNodeWithText(context.getString(R.string.mercadopago_connect_button))
            .assertDoesNotExist()
    }

    @Test
    fun expired_session_asks_for_sign_in_and_hides_private_data() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(state = ProviderProfileUiState.SessionExpired, onBack = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.welcome_auth_unauthorized_error))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertDoesNotExist()
    }

    @Test
    fun approved_identity_displays_status_and_local_date_without_verification_action() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val verifiedOn = 1_768_480_496_000L
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(
                    state = ProviderProfileUiState.Ready(provider().copy(
                        identityVerificationStatus = IdentityVerificationStatus.Approved,
                        identityVerifiedOn = verifiedOn,
                    )),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_identity_approved))
            .performScrollTo().assertIsDisplayed()
        val expectedDate = DateFormat.getDateInstance(
            DateFormat.MEDIUM,
            context.resources.configuration.locales[0],
        ).format(Date(verifiedOn))
        composeTestRule.onNodeWithText(expectedDate).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.identity_verify_now))
            .assertDoesNotExist()
    }

    @Test
    fun unavailable_identity_does_not_show_approval_date_or_action() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(state = ProviderProfileUiState.Ready(provider()), onBack = {})
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_identity_unavailable))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_identity_date_label))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.identity_verify_now))
            .assertDoesNotExist()
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
