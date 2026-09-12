package com.loresuelvo.serviceprovider.domain.provider

interface ProviderRepository {
    suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome
    suspend fun getProfile(providerId: Int): GetProviderProfileOutcome
}
