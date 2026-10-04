package com.loresuelvo.serviceprovider.acceptance.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.Instant

class NavigationConversionRepository : ProviderConversionRepository {
    val queries = mutableListOf<ConversionQuery>()
    var failure: ConversionOutcome.Failure? = null
    override suspend fun getConversion(query: ConversionQuery): ConversionOutcome {
        queries += query
        failure?.let { return it }
        fun ratio(n: Long, d: Long, p: Double) = ConversionRatio(n, d, p)
        return ConversionOutcome.Success(ProviderConversion(
            ConversionPeriod(query.from!!.toInstant(), query.to!!.toInstant(), "America/Argentina/Buenos_Aires"),
            Instant.parse("2026-10-04T12:00:00Z"),
            ProposalConversion(ProposalStages(20, 12, 9, 8), ProposalRates(
                ConversionStageRates(ratio(12, 20, 60.0), ratio(12, 20, 60.0)),
                ConversionStageRates(ratio(9, 20, 45.0), ratio(9, 12, 75.0)),
                ConversionStageRates(ratio(8, 20, 40.0), ratio(8, 9, 88.89))), 8),
            RequestAcceptance(5, 3, 2, ratio(3, 5, 60.0))))
    }
}
