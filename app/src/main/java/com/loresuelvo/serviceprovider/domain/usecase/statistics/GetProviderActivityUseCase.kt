package com.loresuelvo.serviceprovider.domain.usecase.statistics

import com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery
import com.loresuelvo.serviceprovider.domain.statistics.ProviderActivityRepository

class GetProviderActivityUseCase(private val repository: ProviderActivityRepository) {
    suspend operator fun invoke(query: ActivityQuery) = repository.getActivity(query)
}
