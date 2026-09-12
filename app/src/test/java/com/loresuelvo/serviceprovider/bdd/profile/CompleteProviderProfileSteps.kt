package com.loresuelvo.serviceprovider.bdd.profile

import io.cucumber.java.After
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y

/**
 * Step definitions for provider profile BDD scenarios.
 * Strictly implements only active scenarios (01-CPP, 06-CPP, 02-CPP, 03-CPP, 04-CPP, 05-CPP, 09-CPP).
 */
class CompleteProviderProfileSteps {

    private val world = CompleteProviderProfileWorld()

    @After
    fun teardown() = world.close()

    // --- 01-CPP Steps ---

    @Dado("que el prestador acaba de completar el registro en Auth0")
    fun prestadorCompletaRegistroAuth0() {
        world.seedAuthenticatedSession()
    }

    @Cuando("la app navega al destino de perfil profesional")
    fun appNavegaAlDestinoDePerfilProfesional() {
        world.navigateToProfileDestination()
    }

    @Entonces("el prestador ve un formulario que solicita nombre, apellido y rubro")
    fun prestadorVeFormulario() {
        world.assertFormDisplaysFields()
    }

    @Y("la lista de rubros se carga desde la API")
    fun listaDeRubrosSeCargaDesdeApi() {
        world.assertCategoriesLoadedFromApi()
    }

    // --- 06-CPP Steps ---

    @Dado("que la llamada a la API de rubros falla")
    fun apiDeRubrosFalla() {
        world.seedAuthenticatedSession()
        world.configureCategoryApiFailure()
    }

    @Cuando("se muestra el formulario de perfil")
    fun seMuestraElFormularioDePerfil() {
        world.navigateToProfileDestination()
    }

    @Entonces("se muestra un mensaje de error amigable en lugar de la lista de rubros")
    fun seMuestraErrorAmigable() {
        world.assertFriendlyCategoriesError()
    }

    @Y("el prestador puede reintentar la carga de rubros")
    fun prestadorPuedeReintentarCargaRubros() {
        world.retryCategoryLoading()
        world.assertCategoryRetryAvailable()
    }

    // --- 02-CPP Steps ---

    @Dado("que los rubros se cargaron correctamente")
    fun rubrosSeCargaronCorrectamente() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.ensureCategoriesLoaded()
    }

    @Cuando("el prestador selecciona un rubro del menú desplegable")
    fun prestadorSeleccionaUnRubro() {
        world.selectFirstCategory()
    }

    @Entonces("el rubro seleccionado se muestra como la opción actual")
    fun rubroSeleccionadoSeMuestraComoOpcionActual() {
        world.assertFirstCategorySelected()
    }

    @Y("el prestador puede cambiar la selección antes de enviar")
    fun prestadorPuedeCambiarLaSeleccion() {
        world.changeCategorySelection()
        world.assertChangedCategorySelected()
    }

    // --- 03-CPP Steps ---

    @Dado("que el prestador está en el formulario de perfil")
    fun prestadorEstaEnFormularioDePerfil() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
    }

    @Cuando("el prestador intenta continuar con el nombre vacío")
    fun prestadorIntentaContinuarConNombreVacio() {
        world.enterName("")
        world.attemptSubmit()
    }

    @Entonces("aparece un mensaje de validación junto al campo de nombre")
    fun apareceMensajeValidacionNombre() {
        world.assertMissingNameError()
    }

    @Y("el formulario no se envía")
    fun formularioNoSeEnvia() {
        world.assertFormNotSubmitted()
    }

    // --- 04-CPP Steps ---

    @Dado("que el prestador ingresó un nombre válido")
    fun prestadorIngresoNombreValido() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.enterName("Carlos")
    }

    @Cuando("el prestador intenta continuar con el apellido vacío")
    fun prestadorIntentaContinuarConApellidoVacio() {
        world.enterSurname("")
        world.attemptSubmit()
    }

    @Entonces("aparece un mensaje de validación junto al campo de apellido")
    fun apareceMensajeValidacionApellido() {
        world.assertMissingSurnameError()
    }

    // --- 05-CPP Steps ---

    @Dado("que el prestador ingresó un nombre y apellido válidos")
    fun prestadorIngresoNombreYApellidoValidos() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.enterName("Carlos")
        world.enterSurname("Gómez")
    }

    @Cuando("el prestador intenta continuar sin seleccionar un rubro")
    fun prestadorIntentaContinuarSinSeleccionarRubro() {
        world.attemptSubmit()
    }

    @Entonces("aparece un mensaje de validación para el campo de rubro")
    fun apareceMensajeValidacionRubro() {
        world.assertMissingCategoryError()
    }

    // --- 09-CPP Steps ---

    @Dado("que el prestador envió el formulario")
    fun prestadorEnvioElFormulario() {
        world.seedAuthenticatedSession()
        world.navigateToProfileDestination()
        world.prepareValidProfile()
    }

    @Y("el envío está en curso")
    fun envioEstaEnCurso() {
        world.holdRegistrationInFlight()
        world.submitForm()
    }

    @Cuando("el prestador presiona nuevamente el botón de continuar")
    fun prestadorPresionaNuevamenteBotonContinuar() {
        world.pressSubmitButtonAgain()
    }

    @Entonces("no se realiza una segunda llamada a la API")
    fun noSeRealizaSegundaLlamadaApi() {
        world.assertSingleRegistrationCall()
    }

    @Y("el botón permanece deshabilitado con un indicador de carga")
    fun botonPermaneceDeshabilitadoConIndicadorDeCarga() {
        world.assertSubmitDisabledWithLoading()
    }
}
