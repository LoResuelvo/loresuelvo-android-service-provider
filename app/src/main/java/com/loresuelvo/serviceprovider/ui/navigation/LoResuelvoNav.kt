package com.loresuelvo.serviceprovider.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
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
fun LoResuelvoNav() {
    val navController = rememberNavController()

    LoResuelvoNavHost(
        navController = navController,
        startDestination = Route.Welcome.path,
        welcome = { WelcomeRoute(navController) },
        professionalProfile = { CompleteProviderProfileRoute(navController) },
        home = { HomePlaceholder() },
    )
}

/**
 * Welcome screen with its Hilt-provided ViewModel. The Composable
 * bridge passes the activity `Context` (`LocalContext.current`) to
 * the selected ViewModel action: Auth0 requires an Activity-bound
 * context to start its browser flow.
 */
@Composable
private fun WelcomeRoute(navController: NavHostController) {
    val viewModel: WelcomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, navController) {
        viewModel.effects.collect { effect ->
            when (effect) {
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
        onRegisterClick = { viewModel.signup(context) },
        onLoginClick = { viewModel.login(context) },
        onGoogleClick = { viewModel.loginWithGoogle(context) },
    )
}
