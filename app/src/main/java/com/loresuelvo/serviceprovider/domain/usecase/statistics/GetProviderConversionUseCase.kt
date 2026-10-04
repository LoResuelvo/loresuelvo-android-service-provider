package com.loresuelvo.serviceprovider.domain.usecase.statistics

import com.loresuelvo.serviceprovider.domain.statistics.ConversionQuery
import com.loresuelvo.serviceprovider.domain.statistics.ProviderConversionRepository

class GetProviderConversionUseCase(private val repository: ProviderConversionRepository) {
    suspend operator fun invoke(query: ConversionQuery) = repository.getConversion(query)
}
