package com.loresuelvo.serviceprovider.domain.statistics

import java.time.Instant

data class ActivityQuery(val from: Instant, val to: Instant)
data class ActivityPeriod(val from: Instant, val to: Instant, val granularity: String, val timeZone: String)
data class ActivityResults(val confirmedBookings: Long, val reportedCompletions: Long,
    val fullyPaidWorkOrders: Long, val clientsServed: Long, val newClients: Long,
    val returningClients: Long, val agreedValueCents: Long, val averageValueCents: Long?, val currency: String)
data class ActivityBucket(val from: Instant, val to: Instant, val confirmedBookings: Long,
    val reportedCompletions: Long, val fullyPaidWorkOrders: Long)
data class CurrentPending(val requests: Long, val scheduledOrders: Long, val awaitingPaymentOrders: Long)
data class ProviderActivity(val period: ActivityPeriod, val calculatedAt: Instant,
    val results: ActivityResults, val evolution: List<ActivityBucket>, val currentPending: CurrentPending)

sealed interface ActivityOutcome {
    data class Success(val activity: ProviderActivity) : ActivityOutcome
    sealed interface Failure : ActivityOutcome {
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
interface ProviderActivityRepository {
    suspend fun getActivity(query: ActivityQuery): ActivityOutcome
}
