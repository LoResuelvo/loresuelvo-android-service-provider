package com.loresuelvo.serviceprovider.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.loresuelvo.serviceprovider.platform.auth.BrowserAuthenticationLauncher
import com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel
import com.loresuelvo.serviceprovider.ui.screens.auth.WelcomeScreen
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
    val navController = rememberNavController()

    LoResuelvoNavHost(
        navController = navController,
        startDestination = Route.Welcome.path,
        welcome = { WelcomeRoute(navController, browserAuthenticationLauncher) },
        professionalProfile = { CompleteProviderProfileRoute(navController) },
        home = { HomePlaceholder() },
    )
}

/**
 * Welcome route owns the Activity context and sends it only to the outer
 * platform bridge. The bridge returns [AuthenticationOutcome] to the
 * ViewModel, so Android never crosses the UI/domain orchestration boundary.
 */
@Composable
private fun WelcomeRoute(
    navController: NavHostController,
    browserAuthenticationLauncher: BrowserAuthenticationLauncher,
) {
    val viewModel: WelcomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, navController, browserAuthenticationLauncher) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is com.loresuelvo.serviceprovider.ui.auth.WelcomeEffect.LaunchAuthentication ->
                    browserAuthenticationLauncher.launch(
                        activityContext = context,
                        action = effect.action,
                        onResult = viewModel::onAuthenticationResult,
                    )
                com.loresuelvo.serviceprovider.ui.auth.WelcomeEffect.NavigateToProfessionalProfile ->
                    navController.navigate(Route.CompleteProviderProfile.path) {
                        popUpTo(Route.Welcome.path) { inclusive = true }
                        launchSingleTop = true
                    }
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
