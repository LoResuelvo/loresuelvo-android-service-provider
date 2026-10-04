package com.loresuelvo.serviceprovider.ui.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred

fun conversionFixture(issued: Long = 20, noAdvances: Boolean = false): ProviderConversion {
    val contracted = if (noAdvances) 0L else 12L
    val reported = if (noAdvances) 0L else 9L
    val paid = if (noAdvances) 0L else 8L
    fun ratio(n: Long, d: Long, percent: Double) = ConversionRatio(n, d, if (d == 0L) null else percent)
    return ProviderConversion(ConversionPeriod(Instant.parse("2026-09-04T12:00:00Z"),
        Instant.parse("2026-10-04T12:00:00Z"), "America/Argentina/Buenos_Aires"),
        Instant.parse("2026-10-04T12:00:00.123456789Z"),
        ProposalConversion(ProposalStages(issued, contracted, reported, paid),
            ProposalRates(ConversionStageRates(ratio(contracted, issued, if (noAdvances) 0.0 else 60.0),
                ratio(contracted, issued, if (noAdvances) 0.0 else 60.0)),
                ConversionStageRates(ratio(reported, issued, if (noAdvances) 0.0 else 45.0),
                    ratio(reported, contracted, 75.0)),
                ConversionStageRates(ratio(paid, issued, if (noAdvances) 0.0 else 40.0),
                    ratio(paid, reported, 88.89))), issued - contracted),
        RequestAcceptance(5, 3, 2, ratio(3, 5, 60.0)))
}
class ConversionTestRepository : ProviderConversionRepository {
    var outcome: ConversionOutcome = ConversionOutcome.Success(conversionFixture())
    val queries = mutableListOf<ConversionQuery>()
    var gate: CompletableDeferred<Unit>? = null
    var respondToQuery = false
    override suspend fun getConversion(query: ConversionQuery): ConversionOutcome {
        queries += query
        gate?.await()
        return if (respondToQuery) {
            val base = if (query.from?.toInstant() == Instant.parse("2026-09-01T03:00:00Z")) conversionFixture()
                else conversionFixture(5, true)
            ConversionOutcome.Success(base.copy(period = ConversionPeriod(query.from!!.toInstant(),
                query.to!!.toInstant(), "America/Argentina/Buenos_Aires")))
        } else outcome
    }
}
