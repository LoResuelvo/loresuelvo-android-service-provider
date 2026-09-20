package com.loresuelvo.serviceprovider.bdd.identity

import com.loresuelvo.serviceprovider.bdd.profile.CompleteProviderProfileWorld
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.identity.StartIdentityVerificationUseCase
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationEffect
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationViewModel
import com.loresuelvo.serviceprovider.ui.identity.IdentityVerificationFeedback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class OptionalIdentityVerificationWorld : AutoCloseable {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private var profile: CompleteProviderProfileWorld? = null
    private val identityRepository = FakeIdentityVerificationRepository()
    private val identityViewModel = OptionalIdentityVerificationViewModel(
        StartIdentityVerificationUseCase(identityRepository),
    )
    private var effect: OptionalIdentityVerificationEffect? = null
    private var pendingSdkResult: IdentityVerificationResult? = null
    private var expectedFeedback: IdentityVerificationFeedback? = null

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun arrangeValidProviderRegistration() {
        profile = CompleteProviderProfileWorld().also {
            it.seedAuthenticatedSession()
            it.navigateToProfileDestination()
            it.fillValidProfileData()
        }
    }

    fun completeRegistration() {
        checkNotNull(profile).completeRegistrationSuccessfully()
    }

    fun assertProfileCannotBeResubmitted() {
        checkNotNull(profile).assertProfileFormPoppedFromBackstack()
    }

    fun assertOptionalIdentityDestinationRequested() {
        checkNotNull(profile).assertNavigatedToMercadoPago()
    }

    fun arrangeOptionalStep() {
        check(!identityViewModel.uiState.value.loading)
    }

    fun selectLater() {
        identityViewModel.later()
        effect = runBlocking { identityViewModel.effects.first() }
    }

    fun assertNoVerificationStarted() {
        assertFalse(identityViewModel.uiState.value.loading)
        assertEquals(0, identityRepository.calls)
    }

    fun assertMercadoPagoRequestedOnce() {
        assertEquals(OptionalIdentityVerificationEffect.NavigateToMercadoPago, effect)
    }

    fun assertCompletedStepsCannotReopen() {
        assertEquals(OptionalIdentityVerificationEffect.NavigateToMercadoPago, effect)
    }

    fun arrangeValidSession() {
        identityRepository.outcome = StartIdentityVerificationOutcome.Success(
            com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationCredential("temporary-token"),
        )
    }

    fun selectVerifyNow() {
        identityViewModel.verifyNow()
        identityViewModel.verifyNow()
        scheduler.advanceUntilIdle()
        effect = when (identityRepository.outcome) {
            is StartIdentityVerificationOutcome.Success,
            StartIdentityVerificationOutcome.AlreadyApproved,
            StartIdentityVerificationOutcome.Failure.Unauthorized,
            -> runBlocking { identityViewModel.effects.first() }
            else -> null
        }
    }

    fun assertOneSessionAndLaunch() {
        assertEquals(1, identityRepository.calls)
        assertEquals(
            "temporary-token",
            (effect as OptionalIdentityVerificationEffect.LaunchVerification).credential.token,
        )
    }

    fun arrangeActiveAttempt() {
        arrangeValidSession()
        selectVerifyNow()
    }

    fun completeAttempt() {
        identityViewModel.onVerificationResult(IdentityVerificationResult.Completed)
        effect = runBlocking { identityViewModel.effects.first() }
    }

    fun cancelAttempt() {
        identityViewModel.onVerificationResult(IdentityVerificationResult.Cancelled)
    }

    fun failAttempt(permissionDenied: Boolean) {
        pendingSdkResult = if (permissionDenied) {
            IdentityVerificationResult.PermissionDenied
        } else {
            IdentityVerificationResult.Failed
        }
        expectedFeedback = if (permissionDenied) {
            IdentityVerificationFeedback.PermissionDenied
        } else {
            IdentityVerificationFeedback.Failed
        }
    }

    fun returnPendingFailure() {
        identityViewModel.onVerificationResult(checkNotNull(pendingSdkResult))
    }

    fun assertCompletedWithoutPolling() {
        assertMercadoPagoRequestedOnce()
        assertEquals(1, identityRepository.calls)
    }

    fun assertRecoverableFeedback(expected: IdentityVerificationFeedback) {
        assertEquals(expected, identityViewModel.uiState.value.feedback)
        assertFalse(identityViewModel.uiState.value.loading)
    }

    fun assertSafeFailureFeedback() {
        assertRecoverableFeedback(checkNotNull(expectedFeedback))
    }

    fun assertActionsEnabled() {
        assertFalse(identityViewModel.uiState.value.loading)
    }

    fun continueToMercadoPago() {
        identityViewModel.later()
        effect = runBlocking { identityViewModel.effects.first() }
        assertMercadoPagoRequestedOnce()
    }

    fun assertNoIdentityStatusPersisted() {
        assertEquals(1, identityRepository.calls)
        check(identityViewModel.uiState.value.feedback != null)
    }

    fun arrangeSessionResponse(response: String) {
        identityRepository.outcome = when (response) {
            "error de transporte" -> StartIdentityVerificationOutcome.Failure.Network
            "respuesta inválida" -> StartIdentityVerificationOutcome.Failure.InvalidResponse
            "403" -> StartIdentityVerificationOutcome.Failure.Forbidden
            "409" -> StartIdentityVerificationOutcome.AlreadyApproved
            "5xx" -> StartIdentityVerificationOutcome.Failure.Server(503)
            "401" -> StartIdentityVerificationOutcome.Failure.Unauthorized
            else -> error("Unsupported response")
        }
    }

    fun assertNoSdkLaunch() {
        check(effect !is OptionalIdentityVerificationEffect.LaunchVerification)
    }

    fun assertSessionRecovery() {
        when (identityRepository.outcome) {
            StartIdentityVerificationOutcome.AlreadyApproved ->
                assertEquals(OptionalIdentityVerificationEffect.NavigateToMercadoPago, effect)
            StartIdentityVerificationOutcome.Failure.Unauthorized ->
                assertEquals(OptionalIdentityVerificationEffect.NavigateToWelcome, effect)
            else -> assertRecoverableFeedback(IdentityVerificationFeedback.SessionStartFailed)
        }
    }

    fun assertOneRegistrationBoundary() {
        assertEquals(1, identityRepository.calls)
    }

    override fun close() {
        profile?.close()
        Dispatchers.resetMain()
    }

    private class FakeIdentityVerificationRepository : IdentityVerificationRepository {
        var calls = 0
        var outcome: StartIdentityVerificationOutcome = StartIdentityVerificationOutcome.Failure.Network
        override suspend fun start(): StartIdentityVerificationOutcome {
            calls += 1
            return outcome
        }
    }
}
