package com.loresuelvo.serviceprovider.ui.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
import kotlinx.coroutines.CompletableDeferred

class TransactionsTestRepository : CollectionTransactionsRepository {
    val queries = mutableListOf<CollectionTransactionsQuery>()
    val outcomes = ArrayDeque<CollectionTransactionsOutcome>()
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun getTransactions(query: CollectionTransactionsQuery): CollectionTransactionsOutcome {
        queries += query
        gate?.await()
        if (outcomes.isNotEmpty()) return outcomes.removeFirst()
        val rows = listOf(
            CollectionTransaction(3, query.to.minusSeconds(60), CollectionPurpose.BOOKING_DEPOSIT, 120000, "ARS", 8, null),
            CollectionTransaction(2, query.to.minusSeconds(120), CollectionPurpose.SERVICE_BALANCE, 280000, "ARS", 9, 10),
            CollectionTransaction(1, query.to.minusSeconds(180), CollectionPurpose.BOOKING_DEPOSIT, 50000, "ARS", 11, 12)
        ).filter { query.purpose == null || it.purpose == query.purpose }
        val visible = if (query.cursor == null) rows.take(1) else rows.drop(1)
        return CollectionTransactionsOutcome.Success(CollectionTransactions(query.from, query.to,
            "America/Argentina/Buenos_Aires", query.to, "ARS", rows.size.toLong(), rows.sumOf { it.sellerAmountCents },
            visible, if (query.cursor == null && rows.size > 1) "opaque+/cursor=" else null))
    }
}
