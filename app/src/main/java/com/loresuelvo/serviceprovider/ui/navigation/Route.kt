package com.loresuelvo.serviceprovider.ui.navigation

import android.net.Uri

/**
 * Single-source-of-truth for navigation paths in the provider app.
 * Each `Route` carries the path it owns; parameterized routes expose
 * a `buildPath(...)` helper so call sites never assemble the path
 * string by hand (and stay consistent with the `NavHost` arguments).
 *
 * New routes land here as their feature US ships. The Welcome and provider
 * onboarding routes are wired today; the temporary conversation handoff is
 * owned by the request-response story until the chat story replaces it.
 */
sealed class Route(val path: String) {

    /**
     * Pre-authentication landing screen. Shown when the provider has
     * no local session (`AuthSessionStore.getSession() == null`).
     */
    data object Welcome : Route("welcome")

    /**
     * First authenticated destination after provider signup. The route owns
     * the provider's profile-onboarding surface; profile persistence belongs
     * to the later provider-profile story.
     */
    data object CompleteProviderProfile : Route("complete_provider_profile")

    /** Optional identity step shown after provider creation. */
    data object OptionalIdentityVerification : Route("optional_identity_verification")

    /**
     * Provider Mercado Pago linking destination. Reached after provider profile
     * completion succeeds.
     */
    data object MercadoPagoConnect : Route("mercadopago_connect")

    /**
     * Authenticated home / inbox. The actual screen body is added
     * alongside the post-login user stories; today this entry exists
     * so the navigation graph compiles while the Welcome smart-router
     * is being designed.
     */
    data object Home : Route("home")

    /**
     * Provider conversation summaries. The detail route remains a separate
     * destination so the bottom bar can be hidden while a conversation is
     * open.
     */
    data object Messages : Route("messages")

    /** Authenticated provider profile destination. */
    data object Profile : Route("profile")

    /**
     * Read-only detail for one pending provider request. The id is restored by
     * Navigation after Activity recreation; request data stays in the domain
     * and never travels through the route string.
     */
    data object JobRequestDetail : Route("job-request/{jobRequestId}") {
        const val argument: String = "jobRequestId"
        const val resolvedRequestId: String = "resolvedJobRequestId"

        fun buildPath(jobRequestId: Int): String {
            require(jobRequestId > 0) { "jobRequestId must be positive" }
            return "job-request/$jobRequestId"
        }
    }

    /**
     * Temporary provider conversation destination. The following chat story
     * replaces the destination body while keeping this id-based seam.
     */
    data object Conversation : Route("conversation/{conversationId}") {
        const val argument: String = "conversationId"

        fun buildPath(conversationId: Int): String {
            require(conversationId > 0) { "conversationId must be positive" }
            return "conversation/$conversationId"
        }
    }

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
