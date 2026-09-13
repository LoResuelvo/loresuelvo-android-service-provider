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

    @And("la API confirma que su cuenta ya está connected")
    fun apiConfirmaCuentaYaEstaConnected() {
        world.arrangeAccountStatusConnected()
    }

    @Then("la app muestra que la cuenta puede recibir pagos")
    fun appMuestraCuentaPuedeRecibirPagos() {
        world.assertAccountCanReceivePaymentsDisplayed()
    }

    @And("no ofrece iniciar otra autorización")
    fun noOfreceIniciarOtraAutorizacion() {
        world.assertNoOtherAuthorizationOffered()
    }

    @And("ofrece continuar a Home")
    fun ofreceContinuarAHome() {
        world.assertOffersContinueToHome()
    }

    @Given("que el prestador puede conectar su cuenta de Mercado Pago")
    fun prestadorPuedeConectarSuCuentaDeMercadoPago() {
        world.arrangeProviderCanConnect()
    }

    @And("la API devuelve una URL de autorización válida")
    fun apiDevuelveUrlDeAutorizacionValida() {
        world.arrangeValidAuthorizationUrl()
    }

    @When("el prestador selecciona Conectar con Mercado Pago")
    fun prestadorSeleccionaConectarConMercadoPago() {
        world.selectConnectMercadoPago()
    }

    @Then("la app abre la URL de autorización en el navegador")
    fun appAbreUrlDeAutorizacionEnElNavegador() {
        world.assertAuthorizationUrlOpenedInBrowser()
    }

    @And("no solicita credenciales de Mercado Pago dentro de LoResuelvo")
    fun noSolicitaCredencialesDeMercadoPagoDentroDeLoResuelvo() {
        world.assertNoCredentialsRequestedWithinApp()
    }

    @Given("que una solicitud de conexión está en curso")
    fun solicitudDeConexionEstaEnCurso() {
        world.arrangeConnectionRequestInProgress()
    }

    @When("el prestador vuelve a seleccionar Conectar con Mercado Pago")
    fun prestadorVuelveASeleccionarConectarConMercadoPago() {
        world.selectConnectMercadoPago()
    }

    @Then("no se solicita otra autorización a la API")
    fun noSeSolicitaOtraAutorizacionALaApi() {
        world.assertNoOtherAuthorizationRequestedFromApi()
    }

    @And("no se abre otro flujo en el navegador")
    fun noSeAbreOtroFlujoEnElNavegador() {
        world.assertNoOtherFlowOpenedInBrowser()
    }

    @Given("que el prestador autorizó el acceso en Mercado Pago")
    fun prestadorAutorizoAccesoEnMercadoPago() {
        world.arrangeProviderAuthorizedAccess()
    }

    @And("la API confirma el estado connected")
    fun apiConfirmaElEstadoConnected() {
        world.arrangeAccountStatusConnected()
    }

    @When("el prestador regresa a la app mediante el enlace de éxito")
    fun prestadorRegresaALaAppMedianteEnlaceDeExito() {
        world.returnViaSuccessLink()
    }

    @Then("la app consulta el estado actualizado con la API")
    fun appConsultaEstadoActualizadoConLaApi() {
        world.assertStatusQueriedFromApi()
    }

    @And("muestra que la cuenta está conectada y puede recibir pagos")
    fun muestraCuentaConectadaYPuedeRecibirPagos() {
        world.assertAccountCanReceivePaymentsDisplayed()
    }

    @Given("que el prestador regresa mediante el enlace de éxito")
    fun prestadorRegresaMedianteEnlaceDeExito() {
        world.arrangeReturnViaSuccessLink()
    }

    @And("la API informa que la cuenta sigue pendiente de conexión")
    fun apiInformaCuentaSiguePendienteDeConexion() {
        world.arrangeAccountStatusPending()
    }

    @When("la app verifica el estado de la cuenta")
    fun appVerificaEstadoDeLaCuenta() {
        world.verifyAccountStatus()
    }

    @Then("no muestra la conexión como exitosa")
    fun noMuestraConexionComoExitosa() {
        world.assertConnectionNotSuccessful()
    }

    @And("permite volver a consultar el estado o continuar sin conectar")
    fun permiteVolverAConsultarEstadoOContinuarSinConectar() {
        world.assertAllowsRecheckingOrContinuingWithoutConnecting()
    }

    @Given("que el prestador canceló la autorización en Mercado Pago")
    fun prestadorCanceloAutorizacionEnMercadoPago() {
        world.arrangeProviderCancelledAuthorization()
    }

    @When("el prestador regresa a la app mediante el enlace de cancelación")
    fun prestadorRegresaALaAppMedianteEnlaceDeCancelacion() {
        world.returnViaCancellationLink()
    }

    @Then("la app muestra que la conexión no se completó")
    fun appMuestraQueLaConexionNoSeCompleto() {
        world.assertConnectionNotCompletedDisplayed()
    }

    @And("permite reintentar o continuar sin conectar la cuenta")
    fun permiteReintentarOContinuarSinConectarLaCuenta() {
        world.assertAllowsRetryOrContinueWithoutConnecting()
    }

    @Given("que el prestador cerró el navegador sin completar la autorización")
    fun prestadorCerroElNavegadorSinCompletarAutorizacion() {
        world.arrangeProviderClosedBrowserWithoutAuthorizing()
    }

    @When("el prestador vuelve a la app")
    fun prestadorVuelveALaApp() {
        world.returnToApp()
    }

    @Then("la app consulta el estado y conserva la cuenta pendiente de conexión")
    fun appConsultaElEstadoYConservaLaCuentaPendienteDeConexion() {
        world.assertStatusQueriedAndAccountRetainedPending()
    }

    @Given("que el prestador completó su perfil profesional")
    fun prestadorCompletoSuPerfilProfesional() {
        world.arrangeAuthenticatedProviderWithCompleteProfile()
    }

    @And("su cuenta de Mercado Pago está pendiente de conexión")
    fun suCuentaDeMercadoPagoEstaPendienteDeConexion() {
        world.arrangePendingAccountAndOpenScreen()
    }

    @When("el prestador selecciona Continuar sin conectar")
    fun prestadorSeleccionaContinuarSinConectar() {
        world.selectContinueWithoutConnecting()
    }

    @Then("la app permite acceder a Home")
    fun appPermiteAccederAHome() {
        world.assertAccessToHomePermitted()
    }

    @And("no muestra la cuenta como conectada")
    fun noMuestraLaCuentaComoConectada() {
        world.assertAccountNotShownAsConnected()
    }
}
