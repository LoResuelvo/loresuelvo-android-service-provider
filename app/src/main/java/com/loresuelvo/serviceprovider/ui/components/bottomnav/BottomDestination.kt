package com.loresuelvo.serviceprovider.ui.components.bottomnav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.navigation.Route

/**
 * Single source of truth for the provider bottom-navigation tabs. Each
 * destination owns:
 *  - the [route] the bar navigates to (a plain `String` so the bar
 *    stays decoupled from the [Route] sealed class in
 *    [com.loresuelvo.serviceprovider.ui.navigation]),
 *  - the Material icon the bar renders inside the capsule item,
 *  - the localised label used as the icon's content description.
 *
 * The composable ([LoresuelvoBottomBar]) iterates over [all] and
 * renders one click target per entry; adding a third tab later
 * (Profile, Activity, …) is a single new `val` in [Companion] plus
 * a line in [Companion.all]. No composable changes required.
 *
 * The selection state is driven by the current `NavBackStackEntry`
 * route at the host — the bar holds no flags of its own (visibility
 * and selection are derived from the current route, never from a
 * manual boolean).
 *
 * Implementation note: the tabs are modelled as plain `val`s in a
 * companion object (not `data object`s) because the project still
 * uses KAPT for Hilt and the same KAPT/Robolectric test-runtime
 * quirk observed in the consumer codebase can leave the `INSTANCE`
 * static field of `data object`s as null when `listOf(...)` is built
 * inside a Robolectric-driven test. Plain companion `val`s
 * initialise in the companion's `<clinit>` and are guaranteed to be
 * non-null by the time the list is constructed. Production runtime
 * is unaffected.
 */
data class BottomDestination(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {

    companion object {
        val Home: BottomDestination = BottomDestination(
            route = Route.Home.path,
            icon = Icons.Outlined.Home,
            labelRes = R.string.provider_bottom_nav_home,
        )

        val Messages: BottomDestination = BottomDestination(
            route = Route.Messages.path,
            icon = Icons.AutoMirrored.Outlined.Message,
            labelRes = R.string.provider_bottom_nav_messages,
        )

        /**
         * Single source of truth for the bottom-bar order. The
         * capsule renders the items in the order declared here, so
         * re-ordering the tabs is a one-line edit.
         */
        val all: List<BottomDestination> = listOf(Home, Messages)

        /**
         * The bar is visible on any route that maps to a
         * [BottomDestination.route] (Home or the messages list).
         * Detail / auth routes are excluded by this membership
         * test — no per-screen `if` required.
         *
         * Implemented with a `when` rather than `all.any { … }` to
         * sidestep a class-loading order quirk in the Robolectric /
         * KAPT test runtime that left the captured `it` as null in
         * the bytecode. The behaviour is identical to a membership
         * test against `all.map { it.route }`.
         */
        fun shouldShow(currentRoute: String?): Boolean = when (currentRoute) {
            Home.route, Messages.route -> true
            else -> false
        }
    }
}
