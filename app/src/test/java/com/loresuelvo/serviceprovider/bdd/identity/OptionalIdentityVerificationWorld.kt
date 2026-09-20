package com.loresuelvo.serviceprovider.bdd.identity

import com.loresuelvo.serviceprovider.bdd.profile.CompleteProviderProfileWorld

class OptionalIdentityVerificationWorld : AutoCloseable {
    private val profile = CompleteProviderProfileWorld()

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

    override fun close() = profile.close()
}
