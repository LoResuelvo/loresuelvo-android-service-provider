package com.loresuelvo.serviceprovider.bdd.profile

import io.cucumber.java.After
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y

/**
 * Step definitions for provider profile BDD scenarios.
 * Strictly implements only active scenarios (01-CPP and 06-CPP in Batch 1).
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
}
