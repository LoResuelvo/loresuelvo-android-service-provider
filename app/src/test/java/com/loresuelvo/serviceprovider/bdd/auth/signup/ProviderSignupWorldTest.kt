package com.loresuelvo.serviceprovider.bdd.auth.signup

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Focused JVM check for the deterministic 01-PSU world. It proves the
 * app-owned signup delegation, configured request shape, and password-free
 * port with synthetic configuration, without contacting Auth0.
 */
class ProviderSignupWorldTest {

    @Test
    fun selecting_signup_delegates_once_without_password_handling() {
        val world = ProviderSignupWorld()
        try {
            world.seedNoLocalSession()
            world.selectSignup()

            world.assertSignupDelegated()
            world.assertSignupConfiguredForProviderConnection()
            world.assertNoPasswordHandledByApp()
            assertEquals(1, world.signupCalls())
        } finally {
            world.close()
        }
    }
}
