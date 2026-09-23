package com.loresuelvo.serviceprovider.ui.screens.profile

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.identity.IdentityVerificationFeedback
import com.loresuelvo.serviceprovider.ui.profile.ProfileIdentityUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileIdentityCardTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val provider = CurrentAccount.Provider(1, "Ana", "Pérez", "ana@example.com", Category(1, "Gas"), null)

    @Test
    fun `card renders actionable and blocked statuses using the existing policy`() {
        val status = mutableStateOf<IdentityVerificationStatus>(IdentityVerificationStatus.Unverified)
        var starts = 0
        var reloads = 0
        compose.setContent {
            LoresuelvoTheme {
                ProfileIdentityCard(provider.copy(identityVerificationStatus = status.value),
                    ProfileIdentityUiState(), { starts++ }, { reloads++ })
            }
        }
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).assertIsEnabled().performClick()
        assertEquals(1, starts)
        compose.runOnIdle { status.value = IdentityVerificationStatus.Declined }
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG)
            .assertTextEquals(context.getString(R.string.provider_profile_view_retry)).assertIsEnabled()
        listOf(IdentityVerificationStatus.InProgress, IdentityVerificationStatus.InReview,
            IdentityVerificationStatus.Resubmitted, IdentityVerificationStatus.Approved,
            IdentityVerificationStatus.Unavailable).forEach {
            compose.runOnIdle { status.value = it }
            compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).assertIsNotEnabled()
        }
        compose.onNodeWithText(context.getString(R.string.provider_profile_identity_reload)).performClick()
        assertEquals(1, reloads)
    }

    @Test
    fun `busy state disables the action and shows accessible progress then feedback`() {
        val identity = mutableStateOf(ProfileIdentityUiState(loading = true))
        compose.setContent {
            LoresuelvoTheme {
                ProfileIdentityCard(provider.copy(identityVerificationStatus = IdentityVerificationStatus.Unverified),
                    identity.value, {}, {})
            }
        }
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.identity_starting_verification)).assertIsDisplayed()
        compose.runOnIdle { identity.value = ProfileIdentityUiState(feedback = IdentityVerificationFeedback.Failed) }
        compose.onNodeWithText(context.getString(R.string.identity_sdk_error)).assertIsDisplayed()
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).assertIsEnabled()
    }
}
