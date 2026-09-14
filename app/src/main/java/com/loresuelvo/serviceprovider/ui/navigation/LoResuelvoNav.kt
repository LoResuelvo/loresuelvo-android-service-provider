package com.loresuelvo.serviceprovider.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.loresuelvo.serviceprovider.platform.auth.BrowserAuthenticationLauncher
import com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryUiState
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryViewModel
import com.loresuelvo.serviceprovider.ui.screens.entry.ProviderAccountMismatchScreen
import com.loresuelvo.serviceprovider.ui.screens.entry.ProviderEntryErrorScreen
import com.loresuelvo.serviceprovider.ui.screens.entry.ProviderEntryLoadingScreen
import com.loresuelvo.serviceprovider.ui.screens.auth.WelcomeScreen
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationPlaceholderRoute
import com.loresuelvo.serviceprovider.ui.screens.home.ProviderHomeRoute
import com.loresuelvo.serviceprovider.ui.screens.jobrequest.JobRequestDetailRoute
import com.loresuelvo.serviceprovider.ui.screens.messages.ProviderMessagesRoute
import com.loresuelvo.serviceprovider.ui.screens.paymentaccount.MercadoPagoConnectRoute
import com.loresuelvo.serviceprovider.ui.screens.profile.CompleteProviderProfileRoute

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
) {
    val entryViewModel: ProviderEntryViewModel = hiltViewModel()
    val entryState by entryViewModel.uiState.collectAsStateWithLifecycle()

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

                Scaffold(
                    contentWindowInsets = WindowInsets.navigationBars,
                    bottomBar = {
                        ProviderBottomBar(
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
                        )
                    },
                ) { contentPadding ->
                    LoResuelvoNavHost(
                        navController = navController,
                        startDestination = startDestination,
                        contentPadding = contentPadding,
                        welcome = { WelcomeRoute(browserAuthenticationLauncher) },
                        professionalProfile = { CompleteProviderProfileRoute(navController) },
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
                        messages = { ProviderMessagesRoute() },
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
                            ProviderConversationPlaceholderRoute(
                                navController = navController,
                                conversationId = conversationId,
                            )
                        },
                        mercadoPago = {
                            MercadoPagoConnectRoute(
                                navController = navController,
                                onHomeRequested = entryViewModel::refresh,
                                onWelcomeRequested = entryViewModel::refresh,
                            )
                        },
                    )
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
