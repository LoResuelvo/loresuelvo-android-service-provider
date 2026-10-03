package com.loresuelvo.serviceprovider.acceptance.auth

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_TAG
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_LOGOUT_ACTION_TAG
import com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_LOGOUT_CONFIRM_TAG
import com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_PROFILE_DATA_TAG
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Sole new device test: real Activity/Profile/Welcome boundary with fake Auth0. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProviderLogoutNavigationAcceptanceTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var sessionStore: ProviderSignupSessionStore
    private lateinit var browser: ProviderSignupBrowserAuthenticationLauncher
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() {
        hilt.inject()
        val entry = EntryPointAccessors.fromApplication(context, LogoutTestEntryPoint::class.java)
        sessionStore = entry.sessionStore()
        browser = entry.browser()
        entry.accounts().outcome = CurrentAccountOutcome.Success(CurrentAccount.Provider(
            1, "Carlos", "Gomez", "provider@example.test", Category(1, "Plumbing"), null,
        ))
        sessionStore.saveSession(AuthSession(User("auth0|logout", "provider@example.test"), "synthetic-token"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun close() { scenario.close() }

    @Test fun confirmed_sign_out_returns_to_welcome_after_recreation_and_back_cannot_reopen_private_screen() {
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path).performClick()
        compose.onNodeWithTag(PROVIDER_LOGOUT_ACTION_TAG).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.provider_logout_title)).assertIsDisplayed()
        assertNotNull(sessionStore.getSession())
        compose.onNodeWithTag(PROVIDER_LOGOUT_CONFIRM_TAG).performClick()
        assertWelcome()
        assertNull(sessionStore.getSession())
        assertEquals(1, browser.logoutCalls)
        scenario.recreate()
        assertWelcome()
        assertNull(sessionStore.getSession())
        assertEquals(1, browser.logoutCalls)
        Espresso.pressBackUnconditionally()
        assertNotEquals(Lifecycle.State.RESUMED, scenario.state)
        assertNull(sessionStore.getSession())
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertWelcome()
        assertEquals(1, browser.logoutCalls)
    }

    private fun assertWelcome() {
        compose.onNodeWithText(context.getString(R.string.welcome_login)).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_TAG).assertDoesNotExist()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LogoutTestEntryPoint {
    fun sessionStore(): ProviderSignupSessionStore
    fun browser(): ProviderSignupBrowserAuthenticationLauncher
    fun accounts(): ProviderSignupCurrentAccountRepository
}
