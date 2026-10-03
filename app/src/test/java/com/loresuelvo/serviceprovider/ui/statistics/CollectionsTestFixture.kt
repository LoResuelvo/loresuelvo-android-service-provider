package com.loresuelvo.serviceprovider.ui.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred

class CollectionsTestRepository : ProviderCollectionsRepository {
    val queries = mutableListOf<ActivityQuery>()
    var outcome: CollectionsOutcome? = null
    var empty = false
    // Backend-owned account state is independent of its locally recorded verified history.
    var paymentAccountConnected = true
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun getCollections(query: ActivityQuery): CollectionsOutcome {
        queries += query
        gate?.await()
        return outcome ?: CollectionsOutcome.Success(collectionsFixture(query, empty))
    }
}
fun collectionsFixture(query: ActivityQuery = ActivityQuery(Instant.parse("2026-09-03T12:00:00Z"),
    Instant.parse("2026-10-03T12:00:00Z")), empty: Boolean = false): ProviderCollections {
    val amounts = if (empty) CollectionAmounts(0, 0, 0) else CollectionAmounts(120000, 280000, 400000)
    return ProviderCollections(ActivityPeriod(query.from, query.to, query.granularity.name.lowercase(),
        "America/Argentina/Buenos_Aires"), query.to, "ARS", amounts,
        CurrentCollectionPending(PendingCollectionBalance(3, 210000), PendingCollectionBalance(2, 90000)),
        listOf(CollectionBucket(query.from, query.to, amounts)))
}
