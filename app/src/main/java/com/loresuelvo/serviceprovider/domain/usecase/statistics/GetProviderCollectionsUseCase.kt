package com.loresuelvo.serviceprovider.domain.usecase.statistics

import com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery
import com.loresuelvo.serviceprovider.domain.statistics.ProviderCollectionsRepository

class GetProviderCollectionsUseCase(private val repository: ProviderCollectionsRepository) {
    suspend operator fun invoke(query: ActivityQuery) = repository.getCollections(query)
}
