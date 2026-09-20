package com.loresuelvo.serviceprovider.bdd.identity

import io.cucumber.java.After
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y

class OptionalIdentityVerificationSteps {
    private val world = OptionalIdentityVerificationWorld()

    @After
    fun tearDown() = world.close()

    @Dado("que un prestador autenticado envía un perfil profesional válido y la API crea el prestador")
    fun prestadorEnviaPerfilValido() = world.arrangeValidProviderRegistration()

    @Cuando("el registro se completa exitosamente")
    fun registroCompletado() = world.completeRegistration()

    @Entonces("el perfil completado no puede volver a enviarse")
    fun perfilNoPuedeReenviarse() = world.assertProfileCannotBeResubmitted()

    @Y("la app muestra antes de Mercado Pago que la cuenta ya fue creada y la verificación de identidad es opcional")
    fun muestraPasoOpcional() = world.assertOptionalIdentityDestinationRequested()

    @Y("las acciones visibles son {string} y {string}")
    fun muestraAcciones(verificarAhora: String, masTarde: String) {
        check(verificarAhora.isNotBlank() && masTarde.isNotBlank())
        world.assertOptionalIdentityDestinationRequested()
    }
}
