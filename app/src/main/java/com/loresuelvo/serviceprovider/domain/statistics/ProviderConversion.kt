package com.loresuelvo.serviceprovider.domain.statistics

import java.time.OffsetDateTime
import java.time.Instant

data class ConversionQuery(val from: OffsetDateTime? = null, val to: OffsetDateTime? = null)
data class ConversionPeriod(val from: Instant, val to: Instant, val timeZone: String)
data class ConversionRatio(val numerator: Long, val denominator: Long, val percentage: Double?)
data class ConversionStageRates(val cohort: ConversionRatio, val previousStage: ConversionRatio)
data class ProposalStages(val issued: Long, val contracted: Long, val reported: Long, val paid: Long)
data class ProposalRates(val contracted: ConversionStageRates, val reported: ConversionStageRates,
    val paid: ConversionStageRates)
data class ProposalConversion(val stages: ProposalStages, val rates: ProposalRates, val uncontracted: Long)
data class RequestAcceptance(val received: Long, val accepted: Long, val pending: Long,
    val acceptanceRate: ConversionRatio)
data class ProviderConversion(val period: ConversionPeriod, val observedAt: Instant,
    val proposals: ProposalConversion, val requests: RequestAcceptance)

sealed interface ConversionOutcome {
    data class Success(val conversion: ProviderConversion) : ConversionOutcome
    sealed interface Failure : ConversionOutcome {
        data object InvalidQuery : Failure
        data object Unauthorized : Failure
        data object Forbidden : Failure
        data object NotFound : Failure
        data class Server(val status: Int) : Failure
        data object Network : Failure
        data object Malformed : Failure
        data object Unknown : Failure
    }
}
interface ProviderConversionRepository {
    suspend fun getConversion(query: ConversionQuery): ConversionOutcome
}
