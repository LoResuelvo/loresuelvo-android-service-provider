package com.loresuelvo.serviceprovider.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.loresuelvo.serviceprovider.platform.auth.BrowserAuthenticationLauncher
import com.loresuelvo.serviceprovider.platform.identity.IdentityVerificationLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountBrowserLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountReturnHint
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountReturnLinkParser
import com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel
import com.loresuelvo.serviceprovider.ui.components.bottomnav.BottomDestination
import com.loresuelvo.serviceprovider.ui.components.bottomnav.LoresuelvoBottomBar
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryUiState
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryViewModel
import com.loresuelvo.serviceprovider.ui.screens.entry.ProviderAccountMismatchScreen
import com.loresuelvo.serviceprovider.ui.screens.entry.ProviderEntryErrorScreen
import com.loresuelvo.serviceprovider.ui.screens.entry.ProviderEntryLoadingScreen
import com.loresuelvo.serviceprovider.ui.screens.auth.WelcomeScreen
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationRoute
import com.loresuelvo.serviceprovider.ui.screens.home.ProviderHomeRoute
import com.loresuelvo.serviceprovider.ui.screens.proposals.ServiceProposalListRoute
import com.loresuelvo.serviceprovider.ui.screens.jobrequest.JobRequestDetailRoute
import com.loresuelvo.serviceprovider.ui.screens.messages.ProviderMessagesRoute
import com.loresuelvo.serviceprovider.ui.screens.identity.OptionalIdentityVerificationRoute
import com.loresuelvo.serviceprovider.ui.screens.paymentaccount.MercadoPagoConnectRoute
import com.loresuelvo.serviceprovider.ui.screens.profile.CompleteProviderProfileRoute
import com.loresuelvo.serviceprovider.ui.screens.profile.ProviderProfileRoute
import kotlinx.coroutines.flow.StateFlow

/**
 * Composition root for the provider app. Welcome is the initial destination
 * for the unauthenticated flow; successful Auth0 signup hands control to the
 * provider profile-onboarding destination through a one-shot ViewModel
 * effect.
 *
 * `MainActivity` calls `setContent { LoResuelvoNav() }` and owns nothing else.
 * Navigation side effects remain in this composition root.
 */
@Composable
fun LoResuelvoNav(
    browserAuthenticationLauncher: BrowserAuthenticationLauncher,
    identityVerificationLauncher: IdentityVerificationLauncher,
    paymentAccountBrowserLauncher: PaymentAccountBrowserLauncher,
    paymentReturnLinkParser: PaymentAccountReturnLinkParser,
    paymentReturnUrl: StateFlow<String?>,
    onPaymentReturnConsumed: () -> Unit,
) {
    val entryViewModel: ProviderEntryViewModel = hiltViewModel()
    val entryState by entryViewModel.uiState.collectAsStateWithLifecycle()
    val returnUrl by paymentReturnUrl.collectAsStateWithLifecycle()
    var onboardingReturnHint by remember { mutableStateOf<PaymentAccountReturnHint?>(null) }
    var profileReturnRefresh by remember { mutableStateOf(0) }

    when (val state = entryState) {
        ProviderEntryUiState.Loading -> ProviderEntryLoadingScreen()
        ProviderEntryUiState.AccountMismatch -> ProviderAccountMismatchScreen(entryViewModel::continueToWelcome)
        ProviderEntryUiState.RetryableError -> ProviderEntryErrorScreen(entryViewModel::retry)
        else -> {
            val startDestination = when (state) {
                ProviderEntryUiState.Welcome -> Route.Welcome.path
                ProviderEntryUiState.CompleteProviderProfile -> Route.CompleteProviderProfile.path
                is ProviderEntryUiState.Home -> Route.Home.path
                else -> error("Unsupported provider entry state")
            }
            val provider = (state as? ProviderEntryUiState.Home)?.account

            key(startDestination) {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route

                LaunchedEffect(returnUrl, currentRoute) {
                    val hint = returnUrl?.let(paymentReturnLinkParser::parse) ?: return@LaunchedEffect
                    when (currentRoute) {
                        null -> return@LaunchedEffect
                        Route.MercadoPagoConnect.path -> {
                            if (navController.previousBackStackEntry?.destination?.route == Route.Profile.path) {
                                navController.popBackStack(Route.Profile.path, inclusive = false)
                            } else {
                                onboardingReturnHint = hint
                            }
                        }
                        Route.Profile.path -> profileReturnRefresh += 1
                        else -> navController.navigate(Route.Profile.path) { launchSingleTop = true }
                    }
                    onPaymentReturnConsumed()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        contentWindowInsets = WindowInsets.navigationBars,
                        bottomBar = {},
                        containerColor = Color.Transparent,
                    ) { contentPadding ->
                        LoResuelvoNavHost(
                            navController = navController,
                            startDestination = startDestination,
                            contentPadding = contentPadding,
                            welcome = { WelcomeRoute(browserAuthenticationLauncher) },
                            professionalProfile = { CompleteProviderProfileRoute(navController) },
                            optionalIdentityVerification = {
                                OptionalIdentityVerificationRoute(
                                    navController = navController,
                                    launcher = identityVerificationLauncher,
                                )
                            },
                            home = {
                                provider?.let {
                                    ProviderHomeRoute(
                                        navController = navController,
                                        provider = it,
                                        onJobRequestClick = { request ->
                                            navController.navigate(
                                                Route.JobRequestDetail.buildPath(request.id),
                                            )
                                        },
                                    )
                                }
                            },
                            serviceProposals = {
                                ServiceProposalListRoute(
                                    onBack = { navController.popBackStack() },
                                    onConversation = { conversationId ->
                                        navController.navigate(Route.Conversation.buildPath(conversationId)) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            },
                            messages = {
                                ProviderMessagesRoute(
                                    onConversationClick = { conversationId ->
                                        navController.navigate(
                                            Route.Conversation.buildPath(conversationId),
                                        ) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            },
                            profile = {
                                ProviderProfileRoute(
                                    identityLauncher = identityVerificationLauncher,
                                    returnRefreshKey = profileReturnRefresh,
                                    onBack = {
                                        val fromProposal = navController.currentBackStackEntry
                                            ?.savedStateHandle?.remove<Boolean>(Route.Profile.proposalPaymentOrigin) == true
                                        if (fromProposal) {
                                            navController.popBackStack()
                                        } else {
                                            navController.popBackStack(Route.Home.path, inclusive = false)
                                        }
                                    },
                                    onIncompleteProfile = entryViewModel::showIncompleteProfile,
                                    onAccountMismatch = entryViewModel::showAccountMismatch,
                                    onConnectMercadoPago = {
                                        navController.navigate(Route.MercadoPagoConnect.path) {
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            },
                            jobRequestDetail = {
                                JobRequestDetailRoute(
                                    navController = navController,
                                    onAccepted = { requestId, conversationId ->
                                        navController.previousBackStackEntry
                                            ?.savedStateHandle
                                            ?.set(Route.JobRequestDetail.resolvedRequestId, requestId)
                                        navController.navigate(Route.Conversation.buildPath(conversationId)) {
                                            popUpTo(Route.Home.path) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                )
                            },
                            conversation = { conversationId ->
                                ProviderConversationRoute(
                                    navController = navController,
                                    conversationId = conversationId,
                                )
                            },
                            mercadoPago = {
                                val fromProfile = navController.previousBackStackEntry
                                    ?.destination?.route == Route.Profile.path
                                MercadoPagoConnectRoute(
                                    navController = navController,
                                    browserLauncher = paymentAccountBrowserLauncher,
                                    fromProfile = fromProfile,
                                    returnHint = onboardingReturnHint,
                                    onReturnHintConsumed = { onboardingReturnHint = null },
                                    onProfileRequested = {
                                        navController.popBackStack(Route.Profile.path, inclusive = false)
                                    },
                                    onHomeRequested = entryViewModel::refresh,
                                    onWelcomeRequested = entryViewModel::refresh,
                                )
                            },
                        )
                    }

                    if (BottomDestination.shouldShow(currentRoute)) {
                        LoresuelvoBottomBar(
                            currentRoute = currentRoute,
                            onNavigate = { destination ->
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Welcome route owns the Activity context and sends it only to the outer
 * platform bridge. The bridge returns [AuthenticationOutcome] to the
 * ViewModel, so Android never crosses the UI/domain orchestration boundary.
 */
@Composable
private fun WelcomeRoute(
    browserAuthenticationLauncher: BrowserAuthenticationLauncher,
) {
    val viewModel: WelcomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, browserAuthenticationLauncher) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is com.loresuelvo.serviceprovider.ui.auth.WelcomeEffect.LaunchAuthentication ->
                    browserAuthenticationLauncher.launch(
                        activityContext = context,
                        action = effect.action,
                        onResult = viewModel::onAuthenticationResult,
                    )
            }
        }
    }

    WelcomeScreen(
        loading = state.loading,
        error = state.error,
        categories = state.categories,
        onRegisterClick = viewModel::signup,
        onLoginClick = viewModel::login,
        onGoogleClick = viewModel::loginWithGoogle,
    )
}
