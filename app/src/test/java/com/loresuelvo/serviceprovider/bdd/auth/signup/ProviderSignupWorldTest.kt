package com.loresuelvo.serviceprovider.bdd.auth.signup

import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Focused JVM check for the deterministic 01-PSU world. It proves the
 * app-owned signup delegation, signup request hint, and password-free port
 * with synthetic configuration, without contacting Auth0.
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
}
