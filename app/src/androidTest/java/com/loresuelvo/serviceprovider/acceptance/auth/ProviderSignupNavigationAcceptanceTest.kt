package com.loresuelvo.serviceprovider.acceptance.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.auth.AuthProvider
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.User
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-bound proof for the complete provider signup boundary. The test
 * installs deterministic singleton fakes, triggers the production Welcome
 * action, and observes both the same session store used by the production
 * graph and the real profile destination.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProviderSignupNavigationAcceptanceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var authProvider: ProviderSignupAuthProvider
    private lateinit var sessionStore: ProviderSignupSessionStore

    @Before
    fun setUp() {
        hiltRule.inject()
        val entryPoint = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext(),
            ProviderSignupTestEntryPoint::class.java,
        )
        authProvider = entryPoint.authProvider() as ProviderSignupAuthProvider
        sessionStore = entryPoint.sessionStore()
    }

    @Test
    fun successful_signup_persists_the_shared_session_and_reaches_profile() {
        val session = AuthSession(
            user = User(
                id = "auth0|provider",
                email = "provider@example.com",
            ),
            accessToken = "synthetic-provider-access-token",
        )
        authProvider.nextOutcome = AuthenticationOutcome.Success(session)

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.welcome_register))
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.provider_profile_title))
            .assertIsDisplayed()
        assertEquals(session, sessionStore.getSession())
        assertEquals(1, authProvider.signupCalls)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProviderSignupTestEntryPoint {

    fun authProvider(): AuthProvider

    fun sessionStore(): ProviderSignupSessionStore
}
