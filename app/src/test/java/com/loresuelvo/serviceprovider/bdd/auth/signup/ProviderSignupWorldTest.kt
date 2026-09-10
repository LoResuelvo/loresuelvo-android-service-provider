package com.loresuelvo.serviceprovider.bdd.auth.signup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Focused JVM check for the deterministic 01-PSU world. It proves the
 * app-owned signup delegation and password-free port without contacting
 * Auth0 or relying on a tenant connection.
 */
class ProviderSignupWorldTest {

    @Test
    fun selecting_signup_delegates_once_without_password_handling() {
        val world = ProviderSignupWorld()
        try {
            world.seedNoLocalSession()
            world.selectSignup()

            world.assertSignupDelegated()
            world.assertNoPasswordHandledByApp()
            assertEquals(1, world.signupCalls())
        } finally {
            world.close()
        }
    }
}
