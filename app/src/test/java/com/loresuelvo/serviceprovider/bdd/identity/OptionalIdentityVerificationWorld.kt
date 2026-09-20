package com.loresuelvo.serviceprovider.bdd.identity

import com.loresuelvo.serviceprovider.bdd.profile.CompleteProviderProfileWorld
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationEffect
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class OptionalIdentityVerificationWorld : AutoCloseable {
    private val profile = CompleteProviderProfileWorld()
    private val identityViewModel = OptionalIdentityVerificationViewModel()
    private var effect: OptionalIdentityVerificationEffect? = null

    fun arrangeValidProviderRegistration() {
        profile.seedAuthenticatedSession()
        profile.navigateToProfileDestination()
        profile.fillValidProfileData()
    }

    fun completeRegistration() {
        profile.completeRegistrationSuccessfully()
    }

    fun assertProfileCannotBeResubmitted() {
        profile.assertProfileFormPoppedFromBackstack()
    }

    fun assertOptionalIdentityDestinationRequested() {
        profile.assertNavigatedToMercadoPago()
    }

    fun arrangeOptionalStep() = Unit

    fun selectLater() {
        identityViewModel.later()
        effect = runBlocking { identityViewModel.effects.first() }
    }

    fun assertNoVerificationStarted() {
        assertFalse(identityViewModel.uiState.value.loading)
    }

    fun assertMercadoPagoRequestedOnce() {
        assertEquals(OptionalIdentityVerificationEffect.NavigateToMercadoPago, effect)
    }

    fun assertCompletedStepsCannotReopen() {
        assertEquals(OptionalIdentityVerificationEffect.NavigateToMercadoPago, effect)
    }

    override fun close() = profile.close()
}
