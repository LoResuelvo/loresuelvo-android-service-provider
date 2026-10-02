package com.loresuelvo.serviceprovider.acceptance.profile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.domain.usecase.calendar.ConnectCalendarUseCase
import com.loresuelvo.serviceprovider.domain.usecase.identity.StartIdentityVerificationUseCase
import com.loresuelvo.serviceprovider.domain.usecase.paymentaccount.GetPaymentAccountStatusUseCase
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileViewModel
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupCurrentAccountRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupPaymentAccountRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupSessionStore
import com.loresuelvo.serviceprovider.di.CalendarConsentModule
import com.loresuelvo.serviceprovider.di.IdentityVerificationModule
import com.loresuelvo.serviceprovider.domain.account.CalendarConnectionStatus
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConnectionRepository
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.platform.calendar.CalendarConsentLauncher
import com.loresuelvo.serviceprovider.platform.identity.IdentityVerificationLauncher
import com.loresuelvo.serviceprovider.testing.ProfileCalendarConsentTestActivity
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.profile.*
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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(CalendarConsentModule::class, IdentityVerificationModule::class)
@RunWith(AndroidJUnit4::class)
class ProfileCalendarNavigationTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var dependencies: ProfileCalendarEntryPoint
    private val routeStore = ViewModelStore()
    private val provider = CurrentAccount.Provider(20, "Ana", "Pérez", "ana@example.com", Category(1, "Gas"), null,
        identityVerificationStatus = IdentityVerificationStatus.Unverified,
        calendarConnectionStatus = CalendarConnectionStatus.Disconnected)

    @Before
    fun setUp() {
        hiltRule.inject()
        dependencies = EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), ProfileCalendarEntryPoint::class.java)
        dependencies.account().outcome = CurrentAccountOutcome.Success(provider)
        dependencies.payment().outcome = PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.PENDING))
        dependencies.session().saveSession(AuthSession(User("auth0|calendar-device", "ana@example.com"), "device-token"))
        compose.waitForIdle()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path).performClick()
        compose.waitForIdle()
    }

    @After
    fun closeConsentActivity() {
        compose.runOnUiThread {
            routeStore.clear()
            Stage.values().flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .filterIsInstance<ProfileCalendarConsentTestActivity>().distinct().forEach { it.finish() }
        }
    }

    @Test
    fun labelled_action_and_loading_prevent_duplicate_consent_and_identity() {
        dependencies.calendarLauncher().resolveImmediately = false
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).performScrollTo().assertHasClickAction()
            .assertIsEnabled().assertTextEquals(compose.activity.getString(R.string.provider_profile_calendar_connect))
        start()
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROFILE_CALENDAR_LOADING_TAG).assertExists()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).performScrollTo().assertIsNotEnabled()
        assertEquals(1, dependencies.calendarLauncher().calls)
        assertEquals(0, dependencies.calendar().calls)
        assertEquals(0, dependencies.identity().calls)
    }

    @Test
    fun real_activity_result_confirms_only_backend_status_and_does_not_repeat_on_reentry() {
        start(); finishConsent(Activity.RESULT_OK, authorized = true)
        assertConnected()
        assertEquals(1, dependencies.calendarLauncher().parsedResults)
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path).performClick()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.activityRule.scenario.recreate()
        assertConnected()
        assertEquals(1, dependencies.calendarLauncher().calls)
        assertEquals(1, dependencies.calendar().calls)
    }

    @Test
    fun rotation_after_resolution_launch_delivers_one_result_to_restored_registration() {
        start(); awaitConsent()
        recreateBackgroundProfile()
        finishConsent(Activity.RESULT_OK, authorized = true)
        assertConnected()
        assertEquals(1, dependencies.calendarLauncher().calls)
        assertEquals(1, dependencies.calendarLauncher().parsedResults)
        assertEquals(1, dependencies.calendar().calls)
    }

    @Test
    fun cancellation_returns_to_profile_with_retry_and_preserves_payment_navigation() {
        start(); finishConsent(Activity.RESULT_CANCELED)
        assertRecoverable(R.string.provider_profile_calendar_cancelled)
        assertEquals(0, dependencies.calendarLauncher().parsedResults)
        compose.onNodeWithText(compose.activity.getString(R.string.mercadopago_connect_button))
            .performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.mercadopago_connect_title)).assertExists()
        assertEquals(0, dependencies.calendar().calls)
    }

    @Test
    fun denial_returns_to_profile_without_success_and_allows_another_attempt() {
        start(); finishConsent(Activity.RESULT_OK, authorized = false)
        assertRecoverable(R.string.provider_profile_calendar_denied)
        assertEquals(0, dependencies.calendar().calls)
        dependencies.calendarLauncher().resolveImmediately = false
        start()
        assertEquals(2, dependencies.calendarLauncher().calls)
    }

    @Test
    fun rotation_before_sdk_resolution_recovers_and_ignores_old_callbacks() {
        dependencies.calendarLauncher().resolveImmediately = false
        start()
        compose.activityRule.scenario.recreate()
        assertRecoverable(R.string.provider_profile_calendar_cancelled)
        start()
        compose.runOnUiThread {
            dependencies.calendarLauncher().resolve(0)
            dependencies.calendarLauncher().completeWithoutResolution(CalendarConsentResult.Authorized("obsolete"), 0)
        }
        compose.waitForIdle()
        assertEquals(0, dependencies.calendar().calls)
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).assertIsNotEnabled()
        assertEquals(2, dependencies.calendarLauncher().calls)
    }

    @Test
    fun rotation_while_resolution_waits_for_resume_recovers_without_launching_consent() {
        dependencies.calendarLauncher().resolveImmediately = false
        start()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.runOnUiThread { dependencies.calendarLauncher().resolve() }
        recreateBackgroundProfile()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertRecoverable(R.string.provider_profile_calendar_cancelled)
        assertEquals(1, dependencies.calendarLauncher().calls)
        assertEquals(0, dependencies.calendarLauncher().parsedResults)
        assertEquals(0, dependencies.calendar().calls)
    }

    @Test
    fun stale_callback_after_session_replacement_never_posts_or_displays_old_private_profile() {
        dependencies.calendarLauncher().resolveImmediately = false
        start()
        compose.runOnUiThread { dependencies.session().clearSession() }
        compose.waitForIdle()
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertDoesNotExist()
        compose.onNodeWithText("Ana Pérez").assertDoesNotExist()
        dependencies.account().outcome = CurrentAccountOutcome.Success(provider.copy(id = 99, name = "New", email = "new@example.com"))
        compose.runOnUiThread {
            dependencies.session().saveSession(AuthSession(User("other", "new@example.com"), "other-token"))
        }
        compose.waitForIdle()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path).performClick()
        compose.runOnUiThread { dependencies.calendarLauncher().completeWithoutResolution(CalendarConsentResult.Authorized("obsolete"), 0) }
        compose.waitForIdle()
        compose.onNodeWithText("Ana Pérez").assertDoesNotExist()
        compose.onNodeWithText("New Pérez").assertExists()
        assertEquals(0, dependencies.calendar().calls)
    }

    @Test
    fun identity_return_preserves_confirmed_calendar_and_payment_state() {
        start(); finishConsent(Activity.RESULT_OK, authorized = true)
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).performScrollTo()
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.5f) }
        compose.onNodeWithTag(PROFILE_IDENTITY_ACTION_TAG).performClick()
        compose.waitForIdle()
        dependencies.payment().outcome = PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.CONNECTED))
        compose.runOnUiThread { dependencies.identityLauncher().finish(IdentityVerificationResult.Completed) }
        assertConnected()
        compose.onNodeWithTag(PROFILE_PAYMENT_STATUS_TAG).performScrollTo().assertIsDisplayed()
            .assertTextEquals(compose.activity.getString(R.string.provider_profile_connection_connected))
        assertEquals(1, dependencies.calendar().calls)
        assertEquals(1, dependencies.identity().calls)
    }

    @Test
    fun restored_registry_result_cannot_be_reinterpreted_as_new_view_model_attempt() {
        val registry = RecordingConsentRegistry()
        val registryState = mutableStateOf(registry)
        val modelState = mutableStateOf(routeModel("old"))
        showRegistryRoute(registryState, modelState)
        start()
        val oldRequest = registry.requests.single()
        val saved = Bundle()
        compose.runOnUiThread {
            registry.onSaveInstanceState(saved)
            val restored = RecordingConsentRegistry().also { it.onRestoreInstanceState(saved) }
            registryState.value = restored
            modelState.value = routeModel("new")
            modelState.value.onProfileResumed()
        }
        compose.waitForIdle()
        start()
        val restored = registryState.value
        val freshRequest = restored.requests.single()
        compose.runOnUiThread { restored.dispatchResult(oldRequest, Activity.RESULT_OK, Intent().putExtra("authorized", true)) }
        compose.waitForIdle()
        assertEquals(0, dependencies.calendarLauncher().parsedResults)
        assertEquals(0, dependencies.calendar().calls)
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).assertIsNotEnabled()
        compose.runOnUiThread { restored.dispatchResult(freshRequest, Activity.RESULT_OK, Intent().putExtra("authorized", true)) }
        assertConnected()
        assertEquals(1, dependencies.calendarLauncher().parsedResults)
        assertEquals(1, dependencies.calendar().calls)
    }

    @Test
    fun registry_old_result_is_discarded_after_session_replacement_and_new_attempt_in_same_view_model() {
        val registry = RecordingConsentRegistry()
        val model = routeModel("same")
        showRegistryRoute(mutableStateOf(registry), mutableStateOf(model))
        start()
        val oldRequest = registry.requests.single()
        compose.runOnUiThread { dependencies.session().clearSession() }
        compose.waitForIdle()
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertDoesNotExist()
        dependencies.account().outcome = CurrentAccountOutcome.Success(provider.copy(id = 99, name = "New", email = "new@example.com"))
        compose.runOnUiThread {
            dependencies.session().saveSession(AuthSession(User("other", "new@example.com"), "other-token"))
            model.onProfileResumed()
        }
        compose.waitForIdle()
        start()
        val freshRequest = registry.requests.last()
        compose.runOnUiThread { registry.dispatchResult(oldRequest, Activity.RESULT_OK, Intent().putExtra("authorized", true)) }
        compose.waitForIdle()
        assertEquals(0, dependencies.calendarLauncher().parsedResults)
        assertEquals(0, dependencies.calendar().calls)
        compose.onNodeWithText("Ana Pérez").assertDoesNotExist()
        compose.onNodeWithText("New Pérez").assertExists()
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).assertIsNotEnabled()
        compose.runOnUiThread { registry.dispatchResult(freshRequest, Activity.RESULT_OK, Intent().putExtra("authorized", true)) }
        assertConnected()
        assertEquals(1, dependencies.calendar().calls)
    }

    private fun routeModel(key: String): ProviderProfileViewModel = ViewModelProvider(routeStore,
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ProviderProfileViewModel(
                ResolveProviderEntryUseCase(dependencies.session(), dependencies.account()),
                GetPaymentAccountStatusUseCase(dependencies.payment()), dependencies.session(),
                StartIdentityVerificationUseCase(dependencies.identity()),
                ConnectCalendarUseCase(dependencies.calendar(), dependencies.session()),
            ) as T
        })[key, ProviderProfileViewModel::class.java]

    private fun showRegistryRoute(
        registry: androidx.compose.runtime.State<RecordingConsentRegistry>,
        model: androidx.compose.runtime.State<ProviderProfileViewModel>,
    ) {
        compose.runOnUiThread {
            compose.activity.setContent {
                val owner = object : ActivityResultRegistryOwner {
                    override val activityResultRegistry = registry.value
                }
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    ProviderProfileRoute(onBack = {}, identityLauncher = dependencies.identityLauncher(),
                        calendarLauncher = dependencies.calendarLauncher(), viewModel = model.value)
                }
            }
        }
        compose.waitForIdle()
    }

    // Only the external launch is faked; registration, saved routing and result dispatch are real AndroidX.
    private class RecordingConsentRegistry : ActivityResultRegistry() {
        val requests = mutableListOf<Int>()
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            requests += requestCode
        }
    }

    private fun start() {
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).performScrollTo().assertIsEnabled()
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.5f) }
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).performClick()
        compose.waitForIdle()
    }

    private fun recreateBackgroundProfile() {
        val previous = compose.activity
        // ActivityScenario.recreate temporarily requests RESUMED, which would deliver
        // queued consent callbacks or wait forever while the consent activity covers Profile.
        compose.runOnUiThread { previous.recreate() }
        compose.waitUntil(5_000) {
            var recreated = false
            compose.runOnUiThread {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                recreated = monitor.getLifecycleStageOf(previous) == Stage.DESTROYED &&
                    listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED).any { stage ->
                        monitor.getActivitiesInStage(stage).any { it is MainActivity && it !== previous }
                    }
            }
            recreated
        }
    }

    private fun awaitConsent(): ProfileCalendarConsentTestActivity {
        var consent: ProfileCalendarConsentTestActivity? = null
        compose.waitUntil(5_000) {
            compose.runOnUiThread {
                consent = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<ProfileCalendarConsentTestActivity>().singleOrNull()
            }
            consent != null
        }
        return checkNotNull(consent)
    }

    private fun finishConsent(resultCode: Int, authorized: Boolean = false) {
        val activity = awaitConsent()
        compose.runOnUiThread {
            activity.setResult(resultCode, Intent().putExtra("authorized", authorized))
            activity.finish()
        }
        compose.waitForIdle()
    }

    private fun assertConnected() {
        compose.waitForIdle()
        compose.onNodeWithTag(PROFILE_CALENDAR_STATUS_TAG).performScrollTo()
            .assertTextEquals(compose.activity.getString(R.string.provider_profile_calendar_connected))
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROVIDER_PROFILE_SCREEN_TAG).assertIsDisplayed()
    }

    private fun assertRecoverable(feedback: Int) {
        compose.waitForIdle()
        compose.onNodeWithTag(PROFILE_CALENDAR_STATUS_TAG).performScrollTo()
            .assertTextEquals(compose.activity.getString(R.string.provider_profile_calendar_disconnected))
        compose.onNodeWithTag(PROFILE_CALENDAR_ACTION_TAG).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(compose.activity.getString(feedback)).assertExists()
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class CalendarTestBindings {
        @Binds @Singleton abstract fun repository(value: ProfileCalendarTestRepository): CalendarConnectionRepository
        @Binds @Singleton abstract fun launcher(value: ProfileCalendarTestLauncher): CalendarConsentLauncher
        @Binds @Singleton abstract fun identity(value: ProfileIdentityTestRepository): IdentityVerificationRepository
        @Binds @Singleton abstract fun identityLauncher(value: ProfileIdentityTestLauncher): IdentityVerificationLauncher
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProfileCalendarEntryPoint {
    fun session(): ProviderSignupSessionStore
    fun account(): ProviderSignupCurrentAccountRepository
    fun payment(): ProviderSignupPaymentAccountRepository
    fun calendar(): ProfileCalendarTestRepository
    fun calendarLauncher(): ProfileCalendarTestLauncher
    fun identity(): ProfileIdentityTestRepository
    fun identityLauncher(): ProfileIdentityTestLauncher
}
