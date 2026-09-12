package com.loresuelvo.serviceprovider.ui.auth

import com.loresuelvo.serviceprovider.domain.auth.AuthenticationAction

/**
 * One-shot signals emitted by [WelcomeViewModel]. Navigation belongs to the
 * composition root, so the ViewModel exposes an effect instead of embedding
 * a route or a mutable navigation flag in its persistent UI state.
 */
sealed interface WelcomeEffect {
    data class LaunchAuthentication(val action: AuthenticationAction) : WelcomeEffect
    data object NavigateToProfessionalProfile : WelcomeEffect
}
