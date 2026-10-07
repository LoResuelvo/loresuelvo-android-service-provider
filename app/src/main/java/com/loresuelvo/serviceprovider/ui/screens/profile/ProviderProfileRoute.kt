package com.loresuelvo.serviceprovider.ui.screens.profile

import android.app.Activity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.remember
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.withResumed
import kotlinx.coroutines.launch
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult
import com.loresuelvo.serviceprovider.platform.calendar.CalendarConsentLauncher
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.loresuelvo.serviceprovider.platform.identity.IdentityVerificationLauncher
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileViewModel
import com.loresuelvo.serviceprovider.ui.screens.identity.findActivity

@Composable
fun ProviderProfileRoute(
    onBack: () -> Unit,
    identityLauncher: IdentityVerificationLauncher,
    calendarLauncher: CalendarConsentLauncher,
    returnRefreshKey: Int = 0,
    onIncompleteProfile: () -> Unit = {},
    onAccountMismatch: () -> Unit = {},
    onConnectMercadoPago: () -> Unit = {},
    logoutConfirmationVisible: Boolean = false,
    onRequestLogout: () -> Unit = {},
    onDismissLogout: () -> Unit = {},
    onConfirmLogout: () -> Unit = {},
    viewModel: ProviderProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notificationViewModel: com.loresuelvo.serviceprovider.ui.notifications.ProviderNotificationViewModel = hiltViewModel()
    val calendarState by viewModel.calendarState.collectAsStateWithLifecycle()
    val identityState by viewModel.identityState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(notificationViewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            notificationViewModel.effects.collect { effect ->
                when (effect) {
                    com.loresuelvo.serviceprovider.ui.notifications.NotificationUiEffect.OpenSettings ->
                        com.loresuelvo.serviceprovider.platform.notifications.openNotificationSettings(activity)
                }
            }
        }
    }
    val scope = rememberCoroutineScope()
    var calendarAttempt by rememberSaveable { mutableStateOf<String?>(null) }
    val registry = checkNotNull(LocalActivityResultRegistryOwner.current).activityResultRegistry
    var consentResult by remember(activity, viewModel) { mutableStateOf<ActivityResultLauncher<IntentSenderRequest>?>(null) }
    fun registerConsent(id: String): ActivityResultLauncher<IntentSenderRequest> = registry.register(
        "profile-calendar-$id", ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        // The registry owns this immutable attempt, including delayed restored results.
        if (!viewModel.acceptsCalendarResolution(id)) return@register
        if (calendarAttempt == id) calendarAttempt = null
        viewModel.onCalendarResult(id, if (result.resultCode == Activity.RESULT_CANCELED) {
            CalendarConsentResult.Cancelled
        } else calendarLauncher.result(activity, result.data))
    }
    LaunchedEffect(viewModel, calendarLauncher, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.calendarLaunches.collect { launch ->
                if (viewModel.claimCalendarLaunch(launch.attemptId)) {
                    consentResult?.unregister()
                    consentResult = registerConsent(launch.attemptId)
                    calendarLauncher.authorize(activity,
                        onResolution = { sender ->
                            scope.launch {
                                lifecycleOwner.lifecycle.withResumed {
                                    if (viewModel.acceptsCalendarResolution(launch.attemptId)) {
                                        calendarAttempt = launch.attemptId
                                        try {
                                            checkNotNull(consentResult).launch(IntentSenderRequest.Builder(sender).build())
                                        } catch (error: Exception) {
                                            calendarAttempt = null
                                            viewModel.onCalendarResult(launch.attemptId, CalendarConsentResult.Failed)
                                        }
                                    }
                                }
                            }
                        },
                        onResult = { result -> viewModel.onCalendarResult(launch.attemptId, result) },
                    )
                }
            }
        }
    }
    DisposableEffect(activity, identityLauncher, calendarLauncher, registry, viewModel) {
        calendarAttempt?.let { id ->
            if (viewModel.acceptsCalendarResolution(id)) consentResult = registerConsent(id)
            else calendarAttempt = null
        }
        identityLauncher.attach(activity)
        onDispose {
            identityLauncher.detach(activity)
            consentResult?.unregister()
            if (calendarAttempt == null) viewModel.abandonUnlaunchedCalendarConsent()
            if (!activity.isChangingConfigurations) viewModel.leaveProfile()
        }
    }
    LaunchedEffect(viewModel, identityLauncher, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.identityLaunches.collect { launch ->
                if (viewModel.claimIdentityLaunch(launch.attemptId)) {
                    identityLauncher.launch(launch.credential) { result ->
                        viewModel.onIdentityResult(launch.attemptId, result)
                    }
                }
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onProfileResumed()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onProfilePaused() }
    LaunchedEffect(returnRefreshKey) {
        if (returnRefreshKey > 0) viewModel.refresh()
    }
    LaunchedEffect(state) {
        when (state) {
            ProviderProfileUiState.IncompleteProfile -> onIncompleteProfile()
            ProviderProfileUiState.AccountMismatch -> onAccountMismatch()
            else -> Unit
        }
    }
    BackHandler(onBack = onBack)

    ProviderProfileScreen(
        state = state,
        logoutConfirmationVisible = logoutConfirmationVisible,
        onRequestLogout = onRequestLogout,
        onDismissLogout = onDismissLogout,
        onConfirmLogout = onConfirmLogout,
        onNotificationSettings = notificationViewModel::openSettings,
        identityState = identityState,
        calendarState = calendarState,
        onAuthorizeCalendar = viewModel::authorizeCalendar,
        onRetryCalendar = viewModel::retryCalendarConfirmation,
        onVerifyIdentity = viewModel::verifyIdentity,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onRetryPaymentStatus = viewModel::retryPaymentStatus,
        onConnectMercadoPago = onConnectMercadoPago,
    )
}
