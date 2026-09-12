package com.loresuelvo.serviceprovider.domain.usecase.provider

import com.loresuelvo.serviceprovider.domain.provider.GetProviderProfileOutcome
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import javax.inject.Inject

class GetProviderProfileUseCase @Inject constructor(
    private val providerRepository: ProviderRepository,
) {
    suspend operator fun invoke(providerId: Int): GetProviderProfileOutcome =
        providerRepository.getProfile(providerId)
}
