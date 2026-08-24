package com.loresuelvo.serviceprovider.bdd.auth.welcome

import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import io.cucumber.java.After
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import io.cucumber.java.es.Y
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Step definitions for `features/auth/provider-welcome.feature`.
 *
 * Each step talks to [CucumberWorld] (one per scenario) and never
 * touches production types directly: the world wraps the VM,
 * coroutine scheduler and fakes so the steps read like the Gherkin
 * lines they translate.
 */
class WelcomeSteps {

    private val world = CucumberWorld()

    @After
    fun teardown() = world.close()

    // -------- Precondiciones (Given) --------

    @Dado("que el prestador no tiene una sesión local")
    fun prestadorSinSesion() {
        world.seedNoSession()
    }

    @Dado("que el backend responde GET /categories con 6 categorías")
    fun backendRespondeCategorias() {
        world.seedNoSession()
        world.configureBackendCategories(
            CategoriesOutcome.Success(
                listOf(
                    Category(1, "Plomería"),
                    Category(2, "Electricidad"),
                    Category(3, "Pintura"),
                    Category(4, "Gas"),
                    Category(5, "Carpintería"),
                    Category(6, "Aire acondicionado"),
                ),
            ),
        )
    }

    @Dado("que el backend falla al responder GET /categories")
    fun backendFallaCategorias() {
        world.seedNoSession()
        world.configureBackendCategories(
            CategoriesOutcome.Failure.Network(IllegalStateException("backend down")),
        )
    }

    // -------- Acciones (When) --------

    @Cuando("abre la app")
    fun abreLaApp() {
        world.openApp()
    }

    // -------- Aserciones sobre el UiState del VM (Then) --------
    //
    // Los escenarios 01/02/03 del .feature validan contenido visual
    // (logo, badge, 3 pasos, botones). Esas verificaciones se cubren
    // con `WelcomeScreenAcceptanceTest` (Compose-test) — esta capa
    // BDD observa el **estado** del ViewModel para los escenarios
    // que dependen de la carga de categorías.

    @Entonces("el estado de categorías está en Loading")
    fun categoriasEnLoading() {
        assertTrue(
            "Expected categories Loading but was ${world.state().categories}",
            world.state().categories is com.loresuelvo.serviceprovider.ui.auth.WelcomeCategoriesUiState.Loading,
        )
    }

    @Entonces("las categorías tienen 6 elementos")
    fun categoriasCon6Elementos() {
        val state = world.state().categories
        assertTrue(
            "Expected Ready(6) but was $state",
            state is com.loresuelvo.serviceprovider.ui.auth.WelcomeCategoriesUiState.Ready,
        )
        assertEquals(6, (state as com.loresuelvo.serviceprovider.ui.auth.WelcomeCategoriesUiState.Ready).categories.size)
    }

    @Entonces("las categorías colapsan al estado de error")
    fun categoriasEnError() {
        assertTrue(
            "Expected Error but was ${world.state().categories}",
            world.state().categories is com.loresuelvo.serviceprovider.ui.auth.WelcomeCategoriesUiState.Error,
        )
    }

    // -------- Acciones opcionales (para when se destrabe auth) --------

    @Cuando("presiona el botón Registrarme")
    fun presionaRegistrarme() {
        world.triggerSignup()
    }

    @Cuando("presiona el botón Iniciar sesión")
    fun presionaLogin() {
        world.triggerLogin()
    }

    @Cuando("presiona el botón Continuar con Google")
    fun presionaGoogle() {
        world.triggerGoogle()
    }

    @Entonces("se delega el signup al AuthProvider")
    @Y("se delega el signup al AuthProvider")
    fun delegaSignup() {
        assertEquals(1, world.authProvider.signupCalls)
    }

    @Entonces("se delega el login al AuthProvider")
    @Y("se delega el login al AuthProvider")
    fun delegaLogin() {
        assertEquals(1, world.authProvider.loginCalls)
    }

    @Entonces("se delega el login con Google al AuthProvider")
    @Y("se delega el login con Google al AuthProvider")
    fun delegaGoogle() {
        assertEquals(1, world.authProvider.googleCalls)
    }

    // Sanity helper for tests that need a world reference.
    fun world(): CucumberWorld = world.also { assertNotNull(it) }
}