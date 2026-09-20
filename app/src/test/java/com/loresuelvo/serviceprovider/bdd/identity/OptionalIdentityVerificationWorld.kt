package com.loresuelvo.serviceprovider.bdd.identity

import com.loresuelvo.serviceprovider.bdd.profile.CompleteProviderProfileWorld
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationRepository
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.identity.StartIdentityVerificationUseCase
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationEffect
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationViewModel
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
        effect = runBlocking { identityViewModel.effects.first() }
    }

    fun assertOneSessionAndLaunch() {
        assertEquals(1, identityRepository.calls)
        assertEquals(
            "temporary-token",
            (effect as OptionalIdentityVerificationEffect.LaunchVerification).credential.token,
        )
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
