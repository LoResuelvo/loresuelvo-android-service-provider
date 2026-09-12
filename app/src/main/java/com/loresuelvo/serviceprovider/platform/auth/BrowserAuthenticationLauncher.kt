package com.loresuelvo.serviceprovider.platform.auth

import android.content.Context
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationAction
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome

/**
 * Outer-platform bridge for an Activity-bound identity-provider launch.
 *
 * The route owns the Activity [Context] and invokes this bridge; it receives
 * only the pure [AuthenticationOutcome] back. This keeps the dependency
 * direction `ui -> domain <- platform -> data`: neither the ViewModel nor
 * the domain layer imports Android or the data adapter.
 */
fun interface BrowserAuthenticationLauncher {
    fun launch(
        activityContext: Context,
        action: AuthenticationAction,
        onResult: (AuthenticationOutcome) -> Unit,
    )
}
