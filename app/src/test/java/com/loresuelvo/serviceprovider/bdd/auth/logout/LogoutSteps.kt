package com.loresuelvo.serviceprovider.bdd.auth.logout

import com.loresuelvo.serviceprovider.domain.auth.AuthenticationAction
import com.loresuelvo.serviceprovider.domain.auth.AuthenticationOutcome
import com.loresuelvo.serviceprovider.domain.auth.LogoutOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.usecase.auth.EstablishAuthSessionUseCase
import com.loresuelvo.serviceprovider.domain.usecase.category.GetCategoriesUseCase
import com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryUiState
import io.cucumber.java.After
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class LogoutSteps {
    private val world = LogoutWorld()
    private val scope = CoroutineScope(StandardTestDispatcher(world.scheduler))

    @After
    fun close() { scope.cancel(); world.close() }

    @Dado("que estoy en mi Perfil con una sesión activa")
    fun activeProfile() {
        assertTrue(world.viewModel.uiState.value is ProviderEntryUiState.Home)
        assertEquals(world.session, world.store.getSession())
    }

    @Dado("el botón rojo {string} está al final del contenido")
    fun availableAction(@Suppress("UNUSED_PARAMETER") label: String) {
        // Position/color/localized label belong to ProviderLogoutScreenTest.
        // Here the authenticated root must accept the actual Profile action.
        world.openConfirmation()
        assertTrue(world.viewModel.logoutState.value.confirmationVisible)
        world.viewModel.dismissLogout()
    }

    @Dado("(que )está abierto el popup de confirmación")
    fun openConfirmation() {
        world.openConfirmation()
        assertTrue(world.viewModel.logoutState.value.confirmationVisible)
    }

    @Dado("que inicié sesión con {string}")
    fun authenticate(method: String) {
        world.store.clearSession()
        world.drain()
        val categories = object : CategoryRepository {
            override suspend fun getCategories() = CategoriesOutcome.Success(emptyList())
        }
        val welcome = WelcomeViewModel(GetCategoriesUseCase(categories), EstablishAuthSessionUseCase(world.store))
        world.own(welcome)
        val expected = if (method == "Google") AuthenticationAction.GoogleLogin else AuthenticationAction.Login
        scope.launch {
            welcome.effects.collect { effect ->
                assertEquals(expected, (effect as com.loresuelvo.serviceprovider.ui.auth.WelcomeEffect.LaunchAuthentication).action)
                welcome.onAuthenticationResult(AuthenticationOutcome.Success(world.session))
            }
        }
        if (method == "Google") welcome.loginWithGoogle() else welcome.login()
        world.drain()
        assertEquals(world.session, world.store.getSession())
    }

    @Dado("Auth0 puede completar el cierre de sesión")
    fun externalSuccess() { world.nextBrowserOutcome = LogoutOutcome.Success }

    @Dado("Auth0 no puede completar el cierre externo")
    fun externalFailure() { world.nextBrowserOutcome = LogoutOutcome.Failure.Provider(null) }

    @Cuando("pulso {string}")
    fun press(label: String) {
        if (label == "Volver") world.viewModel.dismissLogout() else world.openConfirmation()
        world.drain()
    }

    @Cuando("confirmo {string}")
    fun confirm(@Suppress("UNUSED_PARAMETER") label: String) = world.confirm()

    @Entonces("se muestra un popup redondeado que pregunta si quiero cerrar sesión")
    fun confirmationShown() { assertTrue(world.viewModel.logoutState.value.confirmationVisible) }

    @Entonces("ofrece {string} y {string}")
    fun confirmationActions(@Suppress("UNUSED_PARAMETER") back: String, @Suppress("UNUSED_PARAMETER") confirm: String) {
        // The stateless Screen test verifies both actual dialog controls.
        assertTrue(world.viewModel.logoutState.value.confirmationVisible)
        assertEquals(0, world.browserCalls)
    }

    @Entonces("mi sesión permanece activa hasta confirmar")
    fun stillAuthenticated() { assertEquals(world.session, world.store.getSession()); assertEquals(0, world.store.clearCalls) }

    @Entonces("se cierra el popup y permanezco en Perfil")
    fun cancelled() { assertFalse(world.viewModel.logoutState.value.confirmationVisible); activeProfile() }

    @Entonces("mi sesión permanece activa sin iniciar el cierre externo")
    fun noLogout() { stillAuthenticated(); assertEquals(0, world.browserCalls) }

    @Entonces("se elimina mi sesión local y se cierra la sesión de Auth0")
    fun removed() {
        assertNull(world.store.getSession()); assertNull(world.store.persisted)
        assertEquals(1, world.browserCalls); assertFalse(world.viewModel.logoutState.value.externalLogoutPending)
    }

    @Entonces("se muestra Welcome sin acceso a pantallas privadas con Atrás")
    fun welcome() { assertEquals(ProviderEntryUiState.Welcome, world.viewModel.uiState.value) }

    @Entonces("la próxima apertura de la aplicación muestra Welcome")
    fun relaunch() { val root = world.createRoot(); world.drain(); assertEquals(ProviderEntryUiState.Welcome, root.uiState.value) }

    @Entonces("se elimina igualmente mi sesión local y se muestra Welcome")
    fun localSuccess() { assertNull(world.store.getSession()); assertNull(world.store.persisted); welcome() }

    @Entonces("las pantallas privadas permanecen bloqueadas")
    fun privateAccessBlocked() { world.viewModel.refresh(); world.drain(); welcome() }

    @Entonces("se informa que el cierre externo quedó pendiente y se permite reintentarlo")
    fun retryPending() {
        assertTrue(world.viewModel.logoutState.value.externalLogoutPending)
        world.nextBrowserOutcome = LogoutOutcome.Success
        world.viewModel.retryLogout(); world.completeBrowser()
        assertEquals(2, world.browserCalls); assertFalse(world.viewModel.logoutState.value.externalLogoutPending)
        welcome()
    }
}
