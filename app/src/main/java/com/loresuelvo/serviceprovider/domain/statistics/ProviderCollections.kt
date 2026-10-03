package com.loresuelvo.serviceprovider.domain.statistics

import java.time.Instant

data class CollectionAmounts(val bookingDepositCents: Long, val serviceBalanceCents: Long, val totalCents: Long)
data class PendingCollectionBalance(val orders: Long, val amountCents: Long)
data class CurrentCollectionPending(val scheduled: PendingCollectionBalance, val awaitingPayment: PendingCollectionBalance)
data class CollectionBucket(val from: Instant, val to: Instant, val amounts: CollectionAmounts)
data class CollectionChanges(val bookingDepositCents: ActivityChange, val serviceBalanceCents: ActivityChange,
    val totalCents: ActivityChange)
data class CollectionComparison(val period: ActivityPeriod, val results: CollectionAmounts, val changes: CollectionChanges)
data class ProviderCollections(val period: ActivityPeriod, val calculatedAt: Instant, val currency: String,
    val results: CollectionAmounts, val currentPending: CurrentCollectionPending,
    val evolution: List<CollectionBucket>, val comparison: CollectionComparison? = null)

sealed interface CollectionsOutcome {
    data class Success(val collections: ProviderCollections) : CollectionsOutcome
    sealed interface Failure : CollectionsOutcome {
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
interface ProviderCollectionsRepository {
    suspend fun getCollections(query: ActivityQuery): CollectionsOutcome
}
