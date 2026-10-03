package com.loresuelvo.serviceprovider.domain.statistics

import java.time.Instant

enum class ActivityGranularity { DAY, WEEK, MONTH }
data class ActivityQuery(val from: Instant, val to: Instant,
    val granularity: ActivityGranularity = ActivityGranularity.DAY, val comparePrevious: Boolean = false)
data class ActivityPeriod(val from: Instant, val to: Instant, val granularity: String, val timeZone: String)
data class ActivityResults(val confirmedBookings: Long, val reportedCompletions: Long,
    val fullyPaidWorkOrders: Long, val clientsServed: Long, val newClients: Long,
    val returningClients: Long, val agreedValueCents: Long, val averageValueCents: Long?, val currency: String)
data class ActivityBucket(val from: Instant, val to: Instant, val confirmedBookings: Long,
    val reportedCompletions: Long, val fullyPaidWorkOrders: Long)
data class CurrentPending(val requests: Long, val scheduledOrders: Long, val awaitingPaymentOrders: Long)
data class ProviderActivity(val period: ActivityPeriod, val calculatedAt: Instant,
    val results: ActivityResults, val evolution: List<ActivityBucket>, val currentPending: CurrentPending,
    val comparison: ActivityComparison? = null)
data class ActivityChange(val absolute: Long?, val percentage: Double?)
data class ActivityChanges(val confirmedBookings: ActivityChange, val reportedCompletions: ActivityChange,
    val fullyPaidWorkOrders: ActivityChange, val clientsServed: ActivityChange, val newClients: ActivityChange,
    val returningClients: ActivityChange, val agreedValueCents: ActivityChange, val averageValueCents: ActivityChange)
data class ActivityComparison(val period: ActivityPeriod, val results: ActivityResults, val changes: ActivityChanges)

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
