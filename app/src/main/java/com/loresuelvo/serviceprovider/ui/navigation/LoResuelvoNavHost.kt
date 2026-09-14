package com.loresuelvo.serviceprovider.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

/**
 * Pure graph layer. The host
 * ([com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNav])
 * owns the `Scaffold` slot and the smart-router logic; this
 * composable only declares the routes and the screen-typed
 * Composable for each one. The two layers are split so the host can
 * be unit-tested in isolation — the graph is a pure consumer of the
 * screen composables.
 *
 * `contentPadding` carries the authenticated shell insets. The graph wraps
 * the [NavHost] in a [Box] with that padding so the bottom bar never overlaps
 * the scrollable content of a top-level screen.
 */
@Composable
fun LoResuelvoNavHost(
    navController: NavHostController,
    startDestination: String,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    welcome: @Composable () -> Unit,
    professionalProfile: @Composable () -> Unit,
    home: @Composable () -> Unit,
    messages: @Composable () -> Unit,
    jobRequestDetail: @Composable (Int) -> Unit,
    conversation: @Composable (Int) -> Unit,
    mercadoPago: @Composable () -> Unit = { MercadoPagoPlaceholder() },
) {
    Box(modifier = Modifier.padding(contentPadding)) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(Route.Welcome.path) { welcome() }
            composable(Route.CompleteProviderProfile.path) { professionalProfile() }
            composable(Route.Home.path) { home() }
            composable(Route.Messages.path) { messages() }
            composable(
                route = Route.JobRequestDetail.path,
                arguments = listOf(
                    navArgument(Route.JobRequestDetail.argument) {
                        type = NavType.IntType
                    },
                ),
            ) { entry ->
                jobRequestDetail(
                    entry.arguments?.getInt(Route.JobRequestDetail.argument) ?: -1,
                )
            }
            composable(
                route = Route.Conversation.path,
                arguments = listOf(
                    navArgument(Route.Conversation.argument) {
                        type = NavType.IntType
                    },
                ),
            ) { entry ->
                conversation(
                    entry.arguments?.getInt(Route.Conversation.argument) ?: -1,
                )
            }
            composable(Route.MercadoPagoConnect.path) { mercadoPago() }
        }
    }
}

/**
 * Placeholder Mercado Pago linking body reached after profile completion.
 */
@Composable
fun MercadoPagoPlaceholder() {
    Box(modifier = Modifier.padding(24.dp))
}
