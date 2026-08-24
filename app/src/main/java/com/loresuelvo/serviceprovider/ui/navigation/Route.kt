package com.loresuelvo.serviceprovider.ui.navigation

import android.net.Uri

/**
 * Single-source-of-truth for navigation paths in the provider app.
 * Each `Route` carries the path it owns; parameterized routes expose
 * a `buildPath(...)` helper so call sites never assemble the path
 * string by hand (and stay consistent with the `NavHost` arguments).
 *
 * New routes land here as their feature US ships. The Welcome route
 * is the only one wired today; home / onboarding / chat routes will
 * be added alongside their respective user stories.
 */
sealed class Route(val path: String) {

    /**
     * Pre-authentication landing screen. Shown when the provider has
     * no local session (`AuthSessionStore.getSession() == null`).
     */
    data object Welcome : Route("welcome")

    /**
     * Authenticated home / inbox. The actual screen body is added
     * alongside the post-login user stories; today this entry exists
     * so the navigation graph compiles while the Welcome smart-router
     * is being designed.
     */
    data object Home : Route("home")

    /**
     * Provider category detail / list. Carries the backend
     * `categoryId` for the query and the display name for the
     * header. URL-encoded in the path so accents (`Plomería`)
     * survive navigation round-trips.
     */
    data class Category(val categoryId: Int, val categoryName: String) :
        Route("category/{categoryId}/{categoryName}") {
        companion object {
            fun buildPath(categoryId: Int, categoryName: String): String =
                "category/$categoryId/${Uri.encode(categoryName)}"
        }
    }
}