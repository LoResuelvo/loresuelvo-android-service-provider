package com.loresuelvo.serviceprovider.acceptance.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupCurrentAccountRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupSessionStore
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_TAG
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_PROFILE_DATA_TAG
import com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_PROFILE_SCREEN_TAG
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProviderProfileNavigationAcceptanceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var sessionStore: ProviderSignupSessionStore
    private lateinit var currentAccountRepository: ProviderSignupCurrentAccountRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        val entryPoint = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext(),
            ProviderProfileNavigationTestEntryPoint::class.java,
        )
        sessionStore = entryPoint.sessionStore()
        currentAccountRepository = entryPoint.currentAccountRepository()
        currentAccountRepository.outcome = CurrentAccountOutcome.Success(provider())
        sessionStore.saveSession(
            AuthSession(
                user = User("auth0|provider-device", "provider@example.com"),
                accessToken = "device-access-token",
            ),
        )
    }

    @Test
    fun opens_profile_from_home_and_messages_with_selected_tab_and_back_to_home() {
        composeTestRule.waitForIdle()

        openProfileFrom(Route.Home)
        assertProfileData()

        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path)
            .performClick()
        composeTestRule.waitForIdle()
        openProfileFrom(Route.Messages)
        assertProfileData()

        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.getString(R.string.provider_profile_back),
            )
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Home.path)
            .assertIsSelected()
    }

    @Test
    fun missing_current_account_replaces_the_authenticated_shell_with_onboarding() {
        currentAccountRepository.outcome = CurrentAccountOutcome.Failure.NotFound
        sessionStore.clearSession()
        sessionStore.saveSession(
            AuthSession(
                user = User("auth0|incomplete-provider", "provider@example.com"),
                accessToken = "device-access-token",
            ),
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.provider_profile_title),
            )
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_TAG).assertCountEquals(0)
    }

    @Test
    fun consumer_account_replaces_the_authenticated_shell_with_mismatch_boundary() {
        currentAccountRepository.outcome = CurrentAccountOutcome.Success(CurrentAccount.Consumer)
        sessionStore.clearSession()
        sessionStore.saveSession(
            AuthSession(
                user = User("auth0|consumer", "consumer@example.com"),
                accessToken = "device-access-token",
            ),
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.provider_entry_account_mismatch_title),
            )
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithText("Carlos Gómez").assertDoesNotExist()
    }

    private fun openProfileFrom(destination: Route) {
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + destination.path)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path)
            .performClick()
        composeTestRule.waitForIdle()
    }

    private fun assertProfileData() {
        composeTestRule.onNodeWithTag(PROVIDER_PROFILE_SCREEN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path)
            .assertIsSelected()
        composeTestRule.onNodeWithText("Carlos Gómez").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("carlos@example.com")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Plomería")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("CG").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_TAG)
            .assertIsDisplayed()
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProviderProfileNavigationTestEntryPoint {

    fun sessionStore(): ProviderSignupSessionStore

    fun currentAccountRepository(): ProviderSignupCurrentAccountRepository
}
