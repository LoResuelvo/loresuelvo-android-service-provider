package com.loresuelvo.serviceprovider.domain.usecase.provider

import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import javax.inject.Inject

/**
 * Orchestrates provider profile registration through the [ProviderRepository] port.
 */
class RegisterProviderUseCase @Inject constructor(
    private val providerRepository: ProviderRepository,
) {
    suspend operator fun invoke(command: ProviderRegistrationCommand): RegistrationOutcome =
        providerRepository.register(command)
}
