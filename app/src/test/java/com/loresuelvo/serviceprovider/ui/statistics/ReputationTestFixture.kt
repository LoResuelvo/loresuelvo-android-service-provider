package com.loresuelvo.serviceprovider.ui.statistics

import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred

fun reputationFixture(eligible: Long = 30, empty: Boolean = false): ProviderReputation = ProviderReputation(
    Instant.parse("2026-10-03T12:00:00Z"), if (empty) null else 4.63, if (empty) 0 else 24,
    (1..5).map { RatingCount(it, if (empty) 0 else listOf(0L, 0, 2, 5, 17)[it - 1]) },
    eligible, if (empty) 0 else 24, if (eligible == 0L) null else if (empty) 0.0 else 80.0,
    if (empty) emptyList() else listOf(ReceivedReview(184, 5, "Stored client comment"), ReceivedReview(179, 4, "")),
    if (empty) null else "opaque+/cursor=")

class ReputationTestRepository : ProviderReputationRepository {
    var outcome: ReputationOutcome = ReputationOutcome.Success(reputationFixture())
    var calls = 0
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun getReputation(): ReputationOutcome {
        calls++
        gate?.await()
        return outcome
    }
}
