package com.loresuelvo.serviceprovider.acceptance.profile

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupCurrentAccountRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupSessionStore
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupPaymentAccountRepository
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.di.IdentityVerificationModule
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import com.loresuelvo.serviceprovider.platform.identity.IdentityVerificationLauncher
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.profile.PROFILE_IDENTITY_ACTION_TAG
import com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_PROFILE_SCREEN_TAG
import com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_PROFILE_DATA_TAG
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(IdentityVerificationModule::class)
@RunWith(AndroidJUnit4::class)
class ProfileIdentityNavigationTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var dependencies: ProfileIdentityEntryPoint
    private var callsBeforeReturn = 0
    private val provider = CurrentAccount.Provider(20, "Ana", "Pérez", "ana@example.com", Category(1, "Gas"), null,
        IdentityVerificationStatus.Unverified)

    @Before
    fun setUp() {
        hiltRule.inject()
        dependencies = EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), ProfileIdentityEntryPoint::class.java)
        dependencies.account().outcome = CurrentAccountOutcome.Success(provider)
        dependencies.payment().outcome = PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.PENDING))
        dependencies.session().saveSession(AuthSession(User("auth0|identity-device", "ana@example.com"), "device-token"))
        compose.waitForIdle()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path).performClick()
        compose.waitForIdle()
    }

    @Test
    fun starts_once_and_returns_to_profile_after_cancellation() {
        start()
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).assertIsNotEnabled()
        compose.runOnUiThread { dependencies.launcher().finish(IdentityVerificationResult.Cancelled) }
        assertReturned()
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.identity_cancelled)).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).assertIsSelected()
    }

    @Test
    fun callback_before_resume_refreshes_processing_profile_once() {
        start()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        dependencies.account().outcome = CurrentAccountOutcome.Success(provider.copy(identityVerificationStatus = IdentityVerificationStatus.InProgress))
        compose.runOnUiThread { dependencies.launcher().finish(IdentityVerificationResult.Failed) }
        assertEquals(callsBeforeReturn, dependencies.account().calls)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertReturned()
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun resume_before_callback_refreshes_approved_profile_once() {
        start()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertEquals(callsBeforeReturn, dependencies.account().calls)
        dependencies.account().outcome = CurrentAccountOutcome.Success(provider.copy(identityVerificationStatus = IdentityVerificationStatus.Approved))
        compose.runOnUiThread { dependencies.launcher().finish(IdentityVerificationResult.Completed) }
        assertReturned()
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun recreation_keeps_attempt_without_relaunching() {
        start()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(1, dependencies.launcher().calls)
        assertEquals(1, dependencies.identity().calls)
        compose.runOnUiThread { dependencies.launcher().finish(IdentityVerificationResult.Cancelled) }
        // Activity recreation can also recreate the shell's entry ViewModel and its /me query.
        compose.waitForIdle()
        compose.onNodeWithTag(PROVIDER_PROFILE_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).performScrollTo().assertIsEnabled()
    }

    private fun start() {
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).performScrollTo().assertIsEnabled()
        // Profile shares the shell's overlaid bottom bar; scroll the target clear before tapping.
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).performTouchInput {
            swipeUp(startY = height * 0.7f, endY = height * 0.5f)
        }
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).performClick()
        compose.waitForIdle()
        assertEquals(1, dependencies.identity().calls)
        assertEquals(1, dependencies.launcher().calls)
        callsBeforeReturn = dependencies.account().calls
    }

    private fun assertReturned() {
        compose.waitForIdle()
        assertEquals(callsBeforeReturn + 1, dependencies.account().calls)
        compose.onNodeWithTag(PROVIDER_PROFILE_SCREEN_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path).assertIsSelected()
        compose.onNodeWithText(compose.activity.getString(R.string.mercadopago_connect_title)).assertDoesNotExist()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class IdentityTestBindings {
        @Binds @Singleton abstract fun repository(implementation: ProfileIdentityTestRepository): IdentityVerificationRepository
        @Binds @Singleton abstract fun launcher(implementation: ProfileIdentityTestLauncher): IdentityVerificationLauncher
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProfileIdentityEntryPoint {
    fun session(): ProviderSignupSessionStore
    fun account(): ProviderSignupCurrentAccountRepository
    fun payment(): ProviderSignupPaymentAccountRepository
    fun identity(): ProfileIdentityTestRepository
    fun launcher(): ProfileIdentityTestLauncher
}
