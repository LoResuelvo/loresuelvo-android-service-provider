package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.ProfilePaymentState
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

    @Test
    fun pending_payment_opens_existing_flow_and_calendar_has_no_action() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var connectClicks = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(
                    state = ProviderProfileUiState.Ready(provider(), ProfilePaymentState.Pending),
                    onBack = {},
                    onConnectMercadoPago = { connectClicks += 1 },
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_connection_pending))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_calendar_coming_soon))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(
            hasText(context.getString(R.string.provider_profile_calendar_label)) and hasClickAction(),
        ).assertDoesNotExist()
        composeTestRule.onNodeWithText(context.getString(R.string.mercadopago_connect_button))
            .performScrollTo().performClick()
        assertEquals(1, connectClicks)
    }

    @Test
    fun connected_payment_does_not_offer_another_authorization() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(
                    state = ProviderProfileUiState.Ready(provider(), ProfilePaymentState.Connected),
                    onBack = {},
                )
            }
        }

        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_connection_connected))
            .performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.mercadopago_connect_button))
            .assertDoesNotExist()
    }

    @Test
    fun payment_status_failure_keeps_profile_visible_and_offers_status_retry() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var retries = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderProfileScreen(
                    state = ProviderProfileUiState.Ready(provider(), ProfilePaymentState.Unavailable),
                    onBack = {},
                    onRetryPaymentStatus = { retries += 1 },
                )
            }
        }

        composeTestRule.onNodeWithText("Carlos Gómez").assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_profile_connection_retry))
            .performScrollTo().performClick()
        assertEquals(1, retries)
        composeTestRule.onNodeWithText(context.getString(R.string.mercadopago_connect_button))
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
