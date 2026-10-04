package com.loresuelvo.serviceprovider.domain.statistics

import java.time.Instant

data class RatingCount(val rating: Int, val count: Long)
data class ReceivedReview(val workOrderId: Int, val rating: Int, val description: String)
data class ProviderReputation(val calculatedAt: Instant, val averageRating: Double?, val reviewCount: Long,
    val ratingDistribution: List<RatingCount>, val eligiblePaidOrders: Long, val reviewedPaidOrders: Long,
    val coveragePercentage: Double?, val reviews: List<ReceivedReview>, val nextCursor: String?)

sealed interface ReputationOutcome {
    data class Success(val reputation: ProviderReputation) : ReputationOutcome
    sealed interface Failure : ReputationOutcome {
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
interface ProviderReputationRepository {
    suspend fun getReputation(cursor: String? = null): ReputationOutcome
}
