package com.loresuelvo.serviceprovider.bdd.paymentaccount

import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

class ConnectMercadoPagoSteps {

    private val world = ConnectMercadoPagoWorld()

    @After
    fun tearDown() {
        world.close()
    }

    @Given("que no hay una sesión autenticada")
    fun noHaySesionAutenticada() {
        world.arrangeNoAuthenticatedSession()
    }

    @When("se intenta abrir la pantalla de Mercado Pago")
    fun intentaAbrirPantallaMercadoPago() {
        world.openMercadoPagoScreen()
    }

    @Then("la app solicita iniciar sesión")
    fun appSolicitaIniciarSesion() {
        world.assertLoginPrompted()
    }

    @And("no inicia una autorización de Mercado Pago")
    fun noIniciaAutorizacionMercadoPago() {
        world.assertNoAuthorizationInitiated()
    }

    @Given("^que la cuenta autenticada corresponde a (.+)$")
    fun cuentaAutenticadaCorrespondeA(situacion: String) {
        world.arrangeAuthenticatedAccount(situacion)
    }

    @When("la app evalúa la disponibilidad de la conexión de Mercado Pago")
    fun appEvaluaDisponibilidadDeLaConexion() {
        world.evaluateAvailability()
    }

    @Then("no ofrece iniciar la autorización")
    fun noOfreceIniciarAutorizacion() {
        world.assertNoAuthorizationOffered()
    }

    @And("^muestra una indicación de (.+)$")
    fun muestraIndicacionDe(orientacion: String) {
        world.assertOrientationIndicated(orientacion)
    }

    @Given("que el prestador está autenticado y completó su perfil profesional")
    fun prestadorAutenticadoYCompletoPerfilProfesional() {
        world.arrangeAuthenticatedProviderWithCompleteProfile()
    }

    @And("la API informa que la cuenta está pendiente de conexión")
    fun apiInformaCuentaPendienteDeConexion() {
        world.arrangeAccountStatusPending()
    }

    @When("el prestador abre la pantalla de Mercado Pago")
    fun prestadorAbrePantallaMercadoPago() {
        world.openMercadoPagoScreen()
    }

    @Then("la app muestra que la cuenta está pendiente de conexión")
    fun appMuestraCuentaPendienteDeConexion() {
        world.assertAccountStatusPendingDisplayed()
    }

    @And("ofrece conectar la cuenta o continuar sin conectarla")
    fun ofreceConectarOContinuarSinConectar() {
        world.assertOffersConnectOrContinueWithoutConnecting()
    }
}
