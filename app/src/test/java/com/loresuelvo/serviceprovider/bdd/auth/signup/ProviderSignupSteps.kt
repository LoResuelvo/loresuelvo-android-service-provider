package com.loresuelvo.serviceprovider.bdd.auth.signup

import io.cucumber.java.After
import io.cucumber.java.PendingException
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y

/**
 * Step definitions for the 01-PSU provider signup boundary.
 *
 * The first assertion records the known app-side delegation and then marks
 * the step pending because the feature also names an Auth0 database
 * connection that is not present in this repository. This prevents a test
 * double from falsely proving tenant configuration. The scenario remains
 * `@wip` until that external prerequisite is resolved.
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
        throw PendingException(
            "Auth0 provider database connection identifier and tenant configuration are not available; only app-side signup delegation is verified.",
        )
    }

    @Y("la app nunca solicita ni almacena una contraseña por sí misma")
    fun appDoesNotHandlePassword() {
        world.assertNoPasswordHandledByApp()
    }
}
