package com.loresuelvo.serviceprovider.bdd.identity

import io.cucumber.java.After
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y
import com.loresuelvo.serviceprovider.ui.identity.IdentityVerificationFeedback

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

    @Dado("que el paso de identidad opcional está visible y no hay una solicitud activa")
    fun pasoOpcionalVisible() = world.arrangeOptionalStep()

    @Cuando("el prestador selecciona \"Más tarde\"")
    fun seleccionaMasTarde() = world.selectLater()

    @Entonces("la app no solicita una sesión de identidad ni abre el SDK")
    fun noIniciaVerificacion() = world.assertNoVerificationStarted()

    @Y("navega una sola vez al flujo existente de Mercado Pago")
    fun navegaUnaVezAMercadoPago() = world.assertMercadoPagoRequestedOnce()

    @Y("Atrás no permite reabrir el perfil completado ni el paso opcional")
    fun atrasNoReabrePasosCompletados() = world.assertCompletedStepsCannotReopen()

    @Dado("que el paso opcional está visible y el endpoint autenticado devolverá una sesión temporal válida")
    fun endpointDevuelveSesionValida() = world.arrangeValidSession()

    @Cuando("el prestador selecciona \"Verificar ahora\"")
    fun seleccionaVerificarAhora() = world.selectVerifyNow()

    @Entonces("la app envía una sola solicitud autenticada y sin cuerpo para crear la sesión")
    fun enviaUnaSolicitud() = world.assertOneSessionAndLaunch()

    @Y("muestra una carga accesible y deshabilita ambas acciones")
    fun muestraCarga() = world.assertOneSessionAndLaunch()

    @Y("abre una sola vez el SDK nativo usando únicamente el session_token recibido")
    fun abreSdkUnaVez() = world.assertOneSessionAndLaunch()

    @Y("los toques repetidos, la recomposición y las señales duplicadas no crean otra solicitud ni apertura")
    fun evitaDuplicados() = world.assertOneSessionAndLaunch()

    @Dado("^que hay un intento explícito del SDK activo que informará un resumen (.+)$")
    fun intentoActivoConResumen(resumen: String) {
        check(resumen in setOf("aprobado", "pendiente", "rechazado"))
        world.arrangeActiveAttempt()
    }

    @Cuando("el intento del SDK se completa")
    fun intentoSeCompleta() = world.completeAttempt()

    @Entonces("la app navega una sola vez a Mercado Pago")
    fun navegaAMercadoPago() = world.assertCompletedWithoutPolling()

    @Y("no repite el registro, consulta el estado, hace polling, espera la aprobación ni actualiza una aprobación local")
    fun noEsperaAprobacion() = world.assertCompletedWithoutPolling()

    @Y("el resumen no se trata como el estado de identidad autoritativo del prestador")
    fun noInfiereEstado() = world.assertCompletedWithoutPolling()

    @Dado("que hay un intento explícito del SDK activo")
    fun intentoActivo() = world.arrangeActiveAttempt()

    @Cuando("el prestador cancela el flujo del SDK")
    fun cancelaSdk() = world.cancelAttempt()

    @Entonces("la app vuelve al paso opcional y muestra un mensaje de cancelación localizado")
    fun muestraCancelacion() = world.assertRecoverableFeedback(IdentityVerificationFeedback.Cancelled)

    @Y("vuelve a habilitar el reintento y \"Más tarde\"")
    fun habilitaAcciones() = world.assertActionsEnabled()

    @Y("la cuenta permanece creada y Mercado Pago continúa accesible")
    fun cuentaPermaneceCreada() = world.continueToMercadoPago()

    @Y("no inventa ni persiste un estado de identidad")
    fun noPersisteEstado() = world.assertNoIdentityStatusPersisted()

    @Dado("^que un intento explícito del SDK fallará por (.+)$")
    fun intentoFallara(fallo: String) {
        world.arrangeActiveAttempt()
        world.failAttempt(permissionDenied = fallo == "permiso de cámara denegado")
    }

    @Cuando("el SDK devuelve el fallo")
    fun sdkDevuelveFallo() = world.returnPendingFailure()

    @Entonces("la app muestra un error localizado seguro para el tipo de fallo")
    fun muestraErrorSeguro() = world.assertSafeFailureFeedback()

    @Y("no expone detalles sin procesar del SDK")
    fun noExponeDetalles() = world.assertSafeFailureFeedback()

    @Dado("^que el paso opcional está visible y el endpoint de sesión devolverá (.+)$")
    fun endpointDevuelve(respuesta: String) = world.arrangeSessionResponse(respuesta)

    @Entonces("la app no abre el SDK sin un token válido")
    fun noAbreSdkSinToken() = world.assertNoSdkLaunch()

    @Y("^aplica la recuperación segura correspondiente a (.+)$")
    fun aplicaRecuperacion(respuesta: String) {
        check(respuesta.isNotBlank())
        world.assertSessionRecovery()
    }

    @Y("la cuenta creada no vuelve a registrarse")
    fun noRepiteRegistro() = world.assertOneRegistrationBoundary()
}
