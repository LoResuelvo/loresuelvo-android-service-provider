package com.loresuelvo.serviceprovider.bdd.auth.signup

import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Focused JVM checks for the deterministic provider-signup world. They prove
 * the app-owned signup delegation, signup request hint, password-free port,
 * cancellation, and safe recoverable failure without contacting Auth0.
 */
class ProviderSignupWorldTest {

    @Test
    fun selecting_signup_delegates_once_without_password_handling() {
        val world = ProviderSignupWorld()
        try {
            world.seedNoLocalSession()
            world.selectSignup()

            world.assertSignupDelegated()
            world.assertSignupConfigured()
            world.assertNoPasswordHandledByApp()
            assertEquals(1, world.signupCalls())
        } finally {
            world.close()
        }
    }

    @Test
    fun cancelling_signup_keeps_welcome_available_without_a_session() {
        val world = ProviderSignupWorld()
        try {
            world.seedNoLocalSession()
            world.configureSignupOutcome(AuthenticationOutcome.Cancelled)
            world.cancelSignup()

            world.assertWelcomeRemainsVisible()
            world.assertNoSessionPersisted()
            assertEquals(1, world.signupCalls())
        } finally {
            world.close()
        }
    }

    @Test
    fun recoverable_signup_failure_exposes_safe_error_and_retry_state() {
        val world = ProviderSignupWorld()
        try {
            world.seedNoLocalSession()
            world.configureSignupOutcome(
                AuthenticationOutcome.Failure.Provider(
                    IllegalStateException("synthetic provider failure"),
                ),
            )
            world.finishSignupAttempt()

            world.assertFriendlyAuthenticationError()
            world.assertAuthenticationControlsAvailableForRetry()
            assertEquals(1, world.signupCalls())
        } finally {
            world.close()
        }
    }

    @Test
    fun selecting_another_authentication_action_while_signup_is_active_does_not_start_a_second_flow() {
        val world = ProviderSignupWorld()
        try {
            world.startActiveSignup()
            world.selectAuthenticationAgain()

            world.assertNoSecondAuthenticationFlow()
            world.assertAccessibleLoadingState()
        } finally {
            world.close()
        }
    }
}
