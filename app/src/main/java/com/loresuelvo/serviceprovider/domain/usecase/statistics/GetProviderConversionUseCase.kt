package com.loresuelvo.serviceprovider.domain.usecase.statistics

import com.loresuelvo.serviceprovider.domain.statistics.ConversionOutcome
import com.loresuelvo.serviceprovider.domain.statistics.ConversionQuery
import com.loresuelvo.serviceprovider.domain.statistics.ProviderConversionRepository
import com.loresuelvo.serviceprovider.domain.statistics.validationError
import java.time.Clock

class GetProviderConversionUseCase(
    private val repository: ProviderConversionRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(query: ConversionQuery): ConversionOutcome =
        if (query.validationError(clock.instant()) != null) ConversionOutcome.Failure.InvalidQuery
        else repository.getConversion(query)
}
