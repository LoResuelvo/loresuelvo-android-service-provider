package com.loresuelvo.serviceprovider.domain.provider

/**
 * Port interface for provider registration operations.
 */
interface ProviderRepository {
    suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome
}
