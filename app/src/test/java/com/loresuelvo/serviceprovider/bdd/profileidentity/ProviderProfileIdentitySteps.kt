package com.loresuelvo.serviceprovider.bdd.profileidentity

import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationAction
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import com.loresuelvo.serviceprovider.ui.profile.identity.ProfileIdentityFixture
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.cucumber.java.en.Then
import org.junit.Assert.*

internal class ProviderProfileIdentitySteps {
    private val fixture = ProfileIdentityFixture()
    private var activeAttempt = 0L
    private var callsBeforeReturn = 0
    private var expectedStatus: IdentityVerificationStatus = IdentityVerificationStatus.Unverified

    @Given("que inicié sesión como prestador con mi perfil profesional completo")
    fun authenticatedProvider() {
        checkNotNull(fixture.sessionStore.getSession())
        fixture.accountResponse = { com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome.Success(fixture.provider) }
    }

    @Given("que la consulta de mi perfil devuelve {string}")
    fun profileReturns(status: String) {
        expectedStatus = status(status)
        fixture.provider = fixture.provider.copy(identityVerificationStatus = expectedStatus,
            identityVerifiedOn = if (expectedStatus == IdentityVerificationStatus.Approved) 1_768_480_496_000L else null)
    }

    @When("abro Perfil")
    fun openProfile() = fixture.open()

    @Then("la acción de identidad aparece {string}")
    fun assertAction(action: String) {
        val expected = when {
            action.contains("deshabilitada", ignoreCase = true) -> null
            action.startsWith("Reintentar") -> IdentityVerificationAction.Retry
            else -> IdentityVerificationAction.Start
        }
        assertEquals(expected, ready().provider.identityVerificationStatus.availableAction)
        assertFalse(fixture.viewModel.identityState.value.loading)
    }

    @Then("veo el estado informado y la fecha de aprobación si corresponde")
    fun assertStatusAndDate() {
        assertEquals(expectedStatus, ready().provider.identityVerificationStatus)
        assertEquals(fixture.provider.identityVerifiedOn, ready().provider.identityVerifiedOn)
        assertEquals(1, fixture.accountCalls)
    }

    @Given("que mi perfil permite {string}")
    fun profilePermits(action: String) {
        profileReturns(if (action == "Reintentar") "declined" else "unverified")
        fixture.open()
    }

    @Given("el servicio puede iniciar la identificación")
    fun serviceCanStart() {
        fixture.startResponse = {
            com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome.Success(
                com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationCredential("scenario-session"),
            )
        }
    }

    @When("selecciono {string}")
    fun selectIdentityAction(action: String) {
        assertEquals(if (action == "Reintentar") IdentityVerificationAction.Retry else IdentityVerificationAction.Start,
            ready().provider.identityVerificationStatus.availableAction)
        fixture.viewModel.verifyIdentity()
        fixture.viewModel.verifyIdentity()
        assertTrue(fixture.viewModel.identityState.value.loading)
        fixture.drain()
    }

    @Then("veo que se está iniciando la identificación")
    fun showsProgress() = assertTrue(fixture.viewModel.identityState.value.loading)

    @Then("se abre Didit una sola vez")
    fun opensOnce() {
        assertEquals(1, fixture.startCalls)
        assertEquals(1, fixture.launches.size)
        val launch = fixture.launches.single()
        assertTrue(fixture.viewModel.claimIdentityLaunch(launch.attemptId))
        assertFalse(fixture.viewModel.claimIdentityLaunch(launch.attemptId))
    }

    @Then("la acción queda deshabilitada mientras el intento está activo")
    fun remainsBusy() {
        fixture.viewModel.verifyIdentity()
        fixture.drain()
        assertTrue(fixture.viewModel.identityState.value.loading)
        assertEquals(1, fixture.startCalls)
    }

    @Given("que inicié Didit desde Perfil")
    fun startedFromProfile() {
        fixture.open()
        activeAttempt = fixture.start().attemptId
        callsBeforeReturn = fixture.accountCalls
        fixture.viewModel.onProfilePaused()
    }

    @Given("la siguiente consulta de mi perfil devolverá {string}")
    fun nextProfileStatus(value: String) = profileReturns(value)

    @When("Didit devuelve {string}")
    fun sdkReturns(value: String) {
        val result = when (value) {
            "completado" -> com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult.Completed
            "cancelado" -> com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult.Cancelled
            "error" -> com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult.Failed
            else -> error("Unsupported SDK result: $value")
        }
        fixture.viewModel.onIdentityResult(activeAttempt, result)
        fixture.viewModel.onProfileResumed()
        fixture.drain()
    }

    @Then("vuelvo a Perfil y sus datos se recargan una sola vez")
    fun profileRefreshedOnce() {
        assertEquals(callsBeforeReturn + 1, fixture.accountCalls)
        assertEquals(expectedStatus, ready().provider.identityVerificationStatus)
        assertEquals(1, fixture.startCalls)
    }

    @Then("la acción de identidad queda {string} según esa consulta")
    fun actionFollowsRefreshedProfile(action: String) {
        assertEquals(action == "habilitada", ready().provider.identityVerificationStatus.availableAction != null)
        assertFalse(fixture.viewModel.identityState.value.loading)
    }

    @Then("puedo seguir usando Inicio y Mensajes sin esperar una aprobación")
    fun identityDoesNotBlockAccount() {
        // The same account/session remains usable; device tests prove the actual tab navigation.
        assertNotNull(fixture.sessionStore.getSession())
        assertEquals(fixture.provider.id, ready().provider.id)
        assertFalse(fixture.viewModel.identityState.value.loading)
    }

    @Given("que mi perfil permite iniciar la identificación")
    fun profileAllowsStarting() {
        profilePermits("Verificar identidad")
        callsBeforeReturn = fixture.accountCalls
    }

    @Given("la solicitud de inicio fallará por un problema de red")
    fun sessionRequestWillFail() {
        fixture.startResponse = { com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome.Failure.Network }
    }

    @Then("sigo en Perfil y veo un mensaje de error")
    fun profileShowsStartFailure() {
        assertEquals(fixture.provider.id, ready().provider.id)
        assertEquals(com.loresuelvo.serviceprovider.ui.identity.IdentityVerificationFeedback.SessionStartFailed,
            fixture.viewModel.identityState.value.feedback)
    }

    @Then("Didit no se abre")
    fun sdkDoesNotOpen() = assertTrue(fixture.launches.isEmpty())

    @Then("mi perfil se recarga una sola vez y la acción queda deshabilitada")
    fun refreshDisablesAction() {
        profileRefreshedOnce()
        actionFollowsRefreshedProfile("deshabilitada")
    }

    private fun ready() = fixture.viewModel.uiState.value as ProviderProfileUiState.Ready

    private fun status(value: String): IdentityVerificationStatus = when (value) {
        "unverified" -> IdentityVerificationStatus.Unverified
        "not_started" -> IdentityVerificationStatus.NotStarted
        "awaiting_user" -> IdentityVerificationStatus.AwaitingUser
        "declined" -> IdentityVerificationStatus.Declined
        "abandoned" -> IdentityVerificationStatus.Abandoned
        "expired" -> IdentityVerificationStatus.Expired
        "kyc_expired" -> IdentityVerificationStatus.KycExpired
        "in_progress" -> IdentityVerificationStatus.InProgress
        "in_review" -> IdentityVerificationStatus.InReview
        "resubmitted" -> IdentityVerificationStatus.Resubmitted
        "approved" -> IdentityVerificationStatus.Approved
        "desconocido" -> IdentityVerificationStatus.Unavailable
        else -> error("Unsupported identity status: $value")
    }

    @After
    fun close() = fixture.close()
}
