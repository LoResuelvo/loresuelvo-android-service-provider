package com.loresuelvo.serviceprovider.acceptance.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.Instant

class NavigationReputationRepository : ProviderReputationRepository {
    val cursors = mutableListOf<String?>()
    var failNext = false
    var refreshed = false
    var unauthorized = false
    override suspend fun getReputation(cursor: String?): ReputationOutcome {
        cursors += cursor
        if (unauthorized) return ReputationOutcome.Failure.Unauthorized
        if (failNext) {
            failNext = false
            return ReputationOutcome.Failure.Network
        }
        val page = when (cursor) { null -> 0; "opaque+/first=" -> 1; else -> 2 }
        val rows = if (refreshed) listOf(ReceivedReview(400, 5, "Actual refreshed comment"))
            else (0 until 20).map { index -> ReceivedReview(300 - page * 20 - index,
                if (page * 20 + index < 38) 5 else 4, if (index % 3 == 0) "Actual client comment for work ${300 - page * 20 - index}" else "") }
        return ReputationOutcome.Success(ProviderReputation(Instant.parse(if (refreshed) "2026-10-04T12:00:00Z" else "2026-10-03T12:00:00Z"),
            4.63, 60, listOf(RatingCount(1, 0), RatingCount(2, 0), RatingCount(3, 0), RatingCount(4, 22), RatingCount(5, 38)),
            75, 60, 80.0, rows, if (refreshed || page == 2) null else if (page == 0) "opaque+/first=" else "opaque+/second="))
    }
}
