package com.loresuelvo.serviceprovider.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel
import com.loresuelvo.serviceprovider.ui.screens.auth.WelcomeScreen

/**
 * Composition root for the provider app. Today this is intentionally
 * minimal: Welcome is always the start destination (the smart-router
 * that picks between Welcome and Home based on
 * [com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore]
 * lands alongside the first authenticated user story).
 *
 * `MainActivity` calls `setContent { LoResuelvoNav() }` and owns
 * nothing else. All `LaunchedEffect`, `popUpTo(graph.id)` and
 * `navController.navigate` calls will live here once authenticated
 * routes land.
 */
@Composable
fun LoResuelvoNav() {
    val navController = rememberNavController()

    LoResuelvoNavHost(
        navController = navController,
        startDestination = Route.Welcome.path,
        welcome = { WelcomeRoute() },
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
private fun WelcomeRoute() {
    val viewModel: WelcomeViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    WelcomeScreen(
        error = state.error,
        categories = state.categories,
        onRegisterClick = { viewModel.signup(context) },
        onLoginClick = { viewModel.login(context) },
        onGoogleClick = { viewModel.loginWithGoogle(context) },
    )
}