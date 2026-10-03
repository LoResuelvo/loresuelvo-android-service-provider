package com.loresuelvo.serviceprovider.acceptance.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*

class NavigationCollectionsRepository : ProviderCollectionsRepository {
    val queries = mutableListOf<ActivityQuery>()
    override suspend fun getCollections(query: ActivityQuery): CollectionsOutcome {
        queries += query
        val amounts = CollectionAmounts(120000, 280000, 400000)
        val period = ActivityPeriod(query.from, query.to, query.granularity.name.lowercase(), "America/Argentina/Buenos_Aires")
        return CollectionsOutcome.Success(ProviderCollections(period, query.to, "ARS", amounts,
            CurrentCollectionPending(PendingCollectionBalance(3, 210000), PendingCollectionBalance(2, 90000)),
            (0L..29L).map { interval ->
                val length = java.time.Duration.between(query.from, query.to)
                CollectionBucket(query.from.plus(length.dividedBy(30).multipliedBy(interval)),
                    if (interval == 29L) query.to else query.from.plus(length.dividedBy(30).multipliedBy(interval + 1)),
                    if (interval == 0L) amounts else CollectionAmounts(0, 0, 0))
            }))
    }
}

class NavigationTransactionsRepository : CollectionTransactionsRepository {
    val queries = mutableListOf<CollectionTransactionsQuery>()
    var failNext = false
    override suspend fun getTransactions(query: CollectionTransactionsQuery): CollectionTransactionsOutcome {
        queries += query
        if (failNext) {
            failNext = false
            return CollectionTransactionsOutcome.Failure(CollectionsOutcome.Failure.Network)
        }
        val rows = (1L..30L).map { id ->
            CollectionTransaction(id, query.to.minusSeconds(id * 60),
                if (id % 2 == 0L) CollectionPurpose.SERVICE_BALANCE else CollectionPurpose.BOOKING_DEPOSIT,
                id * 10000, "ARS", id.toInt(), id.toInt())
        }.filter { query.purpose == null || it.purpose == query.purpose }
        val visible = if (query.cursor == null) rows.take(5) else rows.drop(5)
        return CollectionTransactionsOutcome.Success(CollectionTransactions(query.from, query.to,
            "America/Argentina/Buenos_Aires", query.to, "ARS", rows.size.toLong(), rows.sumOf { it.sellerAmountCents },
            visible, if (query.cursor == null) "next" else null))
    }
}
