package com.loresuelvo.serviceprovider.bdd.calendar

import com.loresuelvo.serviceprovider.domain.account.CalendarConnectionStatus
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult
import com.loresuelvo.serviceprovider.domain.calendar.ConnectCalendarOutcome
import com.loresuelvo.serviceprovider.ui.profile.CalendarFeedback
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.calendar.ProfileCalendarFixture
import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal class ProviderCalendarSteps {
    private val world = ProfileCalendarFixture()
    private var failure: String? = null

    @Given("que soy un prestador autenticado con el calendario {string}")
    fun authenticatedCalendarHasStatus(status: String) = world.configureStatus(status)

    @Given("que mi calendario está {string}")
    fun calendarHasStatus(status: String) = world.configureStatus(status)

    @When("abro mi Perfil")
    fun openProfile() = world.open()

    @Then("Google Calendar muestra {string}")
    fun calendarShowsStatus(message: String) {
        val expected = when (message) {
            "Sin vincular" -> CalendarConnectionStatus.Disconnected
            "Vinculado" -> CalendarConnectionStatus.Connected
            "Requiere atención" -> CalendarConnectionStatus.ActionRequired
            else -> error("Unsupported calendar message: $message")
        }
        assertEquals(expected, world.status())
        assertEquals(1, world.accountCalls)
    }

    @And("ofrece {string}")
    fun offersAction(action: String) {
        assertEquals(action != "ninguna acción", world.status().canAuthorize)
        if (action == "ninguna acción") {
            world.viewModel.authorizeCalendar(); world.drain()
            assertTrue(world.launches.isEmpty())
        } else assertEquals(if (action == "Vincular Google Calendar") CalendarConnectionStatus.Disconnected
            else CalendarConnectionStatus.ActionRequired, world.status())
    }

    @And("inicié el consentimiento oficial de Google desde Perfil")
    fun startConsent() { world.open(); world.start() }

    @Given("que inicié la vinculación de Google Calendar desde Perfil")
    fun startConnection() = startConsent()

    @When("autorizo el acceso a mi calendario")
    fun authorizeAccess() {
        world.provider = world.provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Connected)
        world.result(CalendarConsentResult.Authorized("test-server-code"))
    }

    @Then("la aplicación envía el código de autorización a la plataforma")
    fun platformReceivesCode() {
        assertEquals("test-server-code", world.postedCode)
        assertEquals(world.sessionStore.getSession(), world.postedSession)
        assertEquals(1, world.postCalls)
        assertEquals(2, world.accountCalls)
        assertEquals(CalendarConnectionStatus.Connected, world.status())
    }

    @When("{string} el consentimiento de Google")
    fun abandonConsent(result: String) = world.result(when (result) {
        "cancelo" -> CalendarConsentResult.Cancelled
        "deniego" -> CalendarConsentResult.Denied
        else -> error("Unsupported consent result: $result")
    })

    @Then("regreso a Perfil sin mostrar una vinculación exitosa")
    fun profileDoesNotShowFalseSuccess() {
        assertEquals(CalendarConnectionStatus.Disconnected, world.status())
        assertEquals(0, world.postCalls)
        assertFalse(world.viewModel.calendarState.value.loading)
        assertTrue(world.viewModel.calendarState.value.feedback in setOf(CalendarFeedback.Cancelled, CalendarFeedback.Denied))
    }

    @And("puedo reintentar y continuar usando la aplicación")
    fun consentCanBeRetried() {
        world.start()
        assertEquals(2, world.launches.size)
        assertTrue(world.viewModel.uiState.value is ProviderProfileUiState.Ready)
        assertTrue(world.viewModel.calendarState.value.loading)
    }

    @When("ocurre {string}")
    fun connectionFails(error: String) {
        failure = error
        when (error) {
            "un fallo al abrir el consentimiento" -> world.result(CalendarConsentResult.Failed)
            "un fallo de red al enviar el código" -> submitFailure(ConnectCalendarOutcome.Unavailable)
            "el rechazo del código por la plataforma" -> submitFailure(ConnectCalendarOutcome.Rejected)
            "la expiración de mi sesión" -> submitFailure(ConnectCalendarOutcome.Unauthorized)
            "un fallo al refrescar el perfil" -> {
                world.accountResponse = { CurrentAccountOutcome.Failure.Server(503) }
                world.result(CalendarConsentResult.Authorized("test-server-code"))
            }
            else -> error("Unsupported calendar failure: $error")
        }
    }

    private fun submitFailure(outcome: ConnectCalendarOutcome) {
        world.postResponse = { outcome }
        world.result(CalendarConsentResult.Authorized("test-server-code"))
    }

    @Then("se informa el problema sin confirmar una vinculación inexistente")
    fun failureIsReported() {
        if (failure == "la expiración de mi sesión") {
            assertEquals(ProviderProfileUiState.SessionExpired, world.viewModel.uiState.value)
            assertEquals(null, world.sessionStore.getSession())
        } else {
            assertEquals(CalendarConnectionStatus.Disconnected, world.status())
            assertEquals(when (failure) {
                "un fallo al abrir el consentimiento" -> CalendarFeedback.ConsentFailed
                "un fallo de red al enviar el código" -> CalendarFeedback.SubmissionFailed
                "el rechazo del código por la plataforma" -> CalendarFeedback.CodeRejected
                else -> CalendarFeedback.ConfirmationFailed
            }, world.viewModel.calendarState.value.feedback)
        }
    }

    @And("se ofrece {string}")
    fun recoveryWorks(recovery: String) {
        when (recovery) {
            "iniciar sesión nuevamente" -> assertEquals(ProviderProfileUiState.SessionExpired, world.viewModel.uiState.value)
            "reintentar la consulta" -> {
                val posts = world.postCalls
                world.accountResponse = { CurrentAccountOutcome.Success(world.provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Connected)) }
                world.viewModel.retryCalendarConfirmation(); world.drain()
                assertEquals(posts, world.postCalls)
                assertEquals(3, world.accountCalls)
                assertEquals(CalendarConnectionStatus.Connected, world.status())
            }
            "reintentar", "iniciar otro consentimiento" -> {
                world.start()
                assertEquals(2, world.launches.size)
                assertTrue(world.viewModel.calendarState.value.loading)
            }
            else -> error("Unsupported recovery: $recovery")
        }
    }

    @Given("que una vinculación de Google Calendar está en curso")
    fun connectionIsPending() = startConsent()

    @When("intento iniciarla nuevamente")
    fun startAgain() { world.viewModel.authorizeCalendar(); world.drain() }

    @Then("se mantiene un único intento con una indicación de carga")
    fun pendingAttemptIsPreserved() {
        assertTrue(world.viewModel.calendarState.value.loading)
        assertEquals(1, world.launches.size)
    }

    @And("no se duplica el consentimiento ni el envío del código")
    fun requestsAreNotDuplicated() {
        val id = world.launches.single().attemptId
        world.viewModel.onCalendarResult(id, CalendarConsentResult.Authorized("test-server-code"))
        world.viewModel.onCalendarResult(id, CalendarConsentResult.Authorized("duplicate-code"))
        world.drain()
        assertEquals(1, world.launches.size)
        assertEquals(1, world.postCalls)
        assertEquals("test-server-code", world.postedCode)
    }

    @After
    fun close() = world.close()
}
