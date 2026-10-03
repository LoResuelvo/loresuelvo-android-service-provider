package com.loresuelvo.serviceprovider.domain.statistics

import java.time.Instant

enum class CollectionPurpose { BOOKING_DEPOSIT, SERVICE_BALANCE }
data class CollectionTransactionsQuery(val from: Instant, val to: Instant,
    val purpose: CollectionPurpose? = null, val cursor: String? = null, val limit: Int = 20)
data class CollectionTransaction(val id: Long, val verifiedOn: Instant, val purpose: CollectionPurpose,
    val sellerAmountCents: Long, val currency: String, val serviceProposalId: Int, val workOrderId: Int?)
data class CollectionTransactions(val from: Instant, val to: Instant, val timeZone: String,
    val calculatedAt: Instant, val currency: String, val totalCount: Long, val totalAmountCents: Long,
    val transactions: List<CollectionTransaction>, val nextCursor: String?)
sealed interface CollectionTransactionsOutcome {
    data class Success(val page: CollectionTransactions) : CollectionTransactionsOutcome
    data class Failure(val reason: CollectionsOutcome.Failure) : CollectionTransactionsOutcome
}
interface CollectionTransactionsRepository {
    suspend fun getTransactions(query: CollectionTransactionsQuery): CollectionTransactionsOutcome
}
