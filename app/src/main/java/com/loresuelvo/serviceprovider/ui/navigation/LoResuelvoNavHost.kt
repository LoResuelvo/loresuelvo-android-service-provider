package com.loresuelvo.serviceprovider.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.loresuelvo.serviceprovider.R

/**
 * Pure graph layer. The host
 * ([com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNav])
 * owns the `Scaffold` slot and the smart-router logic; this
 * composable only declares the routes and the screen-typed
 * Composable for each one. The two layers are split so the host can
 * be unit-tested in isolation — the graph is a pure consumer of the
 * screen composables.
 *
 * `contentPadding` carries the Scaffold insets (top status bar when
 * visible). The graph wraps the [NavHost] in a [Box] with that
 * padding so the bottom bar (added in a later US) never overlaps the
 * scrollable content of any screen.
 */
@Composable
fun LoResuelvoNavHost(
    navController: NavHostController,
    startDestination: String,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    welcome: @Composable () -> Unit,
    home: @Composable () -> Unit,
) {
    Box(modifier = Modifier.padding(contentPadding)) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(Route.Welcome.path) { welcome() }
            composable(Route.Home.path) { home() }
        }
    }
}

/**
 * Placeholder Home body. Wired into the navigation graph so the
 * Route.Home declaration compiles today; the real home / inbox
 * screen lands in a follow-up US (provider inbox).
 */
@Composable
fun HomePlaceholder() {
    Box(modifier = Modifier.padding(24.dp)) {
        Text(text = stringResource(R.string.home_placeholder_title))
    }
}