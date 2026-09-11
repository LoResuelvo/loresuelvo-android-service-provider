package com.loresuelvo.serviceprovider.bdd.auth.signup

import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import io.cucumber.java.After
import io.cucumber.java.PendingException
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y

/**
 * Step definitions for the 01-PSU provider signup boundary.
 *
 * The first assertions cover app-owned delegation and the adapter's signup
 * request shape. The step remains pending for the tenant-selected connection
 * because that hosted check is human-owned.
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
        world.assertSignupConfigured()
        throw PendingException(
            "Auth0 tenant-selected connection and hosted signup screen require human-owned smoke verification.",
        )
    }

    @Y("la app nunca solicita ni almacena una contraseña por sí misma")
    fun appDoesNotHandlePassword() {
        world.assertNoPasswordHandledByApp()
    }

    @Dado("que el prestador comenzó sin una sesión local")
    fun prestadorComenzoSinSesionAlternativo() {
        world.seedNoLocalSession()
    }

    @Cuando("el prestador cancela el registro en Auth0")
    fun prestadorCancelaRegistro() {
        world.configureSignupOutcome(AuthenticationOutcome.Cancelled)
        world.cancelSignup()
    }

    @Entonces("la pantalla de bienvenida permanece visible")
    fun welcomeRemainsVisible() {
        world.assertWelcomeRemainsVisible()
    }

    @Y("no se persiste ninguna sesión")
    fun noSessionIsPersisted() {
        world.assertNoSessionPersisted()
    }
}
