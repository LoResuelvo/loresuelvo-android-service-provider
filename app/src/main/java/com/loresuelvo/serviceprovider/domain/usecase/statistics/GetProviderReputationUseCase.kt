package com.loresuelvo.serviceprovider.domain.usecase.statistics

import com.loresuelvo.serviceprovider.domain.statistics.ProviderReputationRepository

class GetProviderReputationUseCase(private val repository: ProviderReputationRepository) {
    suspend operator fun invoke(cursor: String? = null) = repository.getReputation(cursor)
}
