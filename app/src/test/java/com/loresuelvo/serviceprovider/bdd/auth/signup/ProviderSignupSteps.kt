package com.loresuelvo.serviceprovider.bdd.auth.signup

import io.cucumber.java.After
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y

/**
 * Step definitions for the 01-PSU provider signup boundary.
 *
 * The first assertion covers the app-owned delegation and the adapter's
 * request shape with a synthetic configuration. It deliberately does not
 * claim that a real Auth0 tenant has the connection available; that hosted
 * check remains human-owned.
 */
class ProviderSignupSteps {

    private val world = ProviderSignupWorld()

    @After
    fun teardown() = world.close()

    @Dado("que el prestador no tiene una sesión local")
    fun prestadorSinSesion() {
        world.seedNoLocalSession()
    }

    @Cuando("el prestador selecciona {string} desde la pantalla de bienvenida")
    fun prestadorSeleccionaAccion(action: String) {
        check(action == "Registrarme") { "Unsupported provider signup action: $action" }
        world.selectSignup()
    }

    @Entonces("la app inicia Auth0 Universal Login en modo de registro para la conexión de correo electrónico y contraseña del prestador")
    fun auth0SignupStartsForProviderConnection() {
        world.assertSignupDelegated()
        world.assertSignupConfiguredForProviderConnection()
    }

    @Y("la app nunca solicita ni almacena una contraseña por sí misma")
    fun appDoesNotHandlePassword() {
        world.assertNoPasswordHandledByApp()
    }
}
