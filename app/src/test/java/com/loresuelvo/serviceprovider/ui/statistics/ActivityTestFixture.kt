package com.loresuelvo.serviceprovider.ui.statistics

import com.loresuelvo.serviceprovider.domain.auth.*
import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

class ActivityTestSessionStore : AuthSessionStore {
    override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "provider@example.com"), "test-token"))
    override fun getSession() = sessionFlow.value
    override fun saveSession(session: AuthSession) { sessionFlow.value = session }
    override fun clearSession() { sessionFlow.value = null }
}
class ActivityTestRepository : ProviderActivityRepository {
    val queries = mutableListOf<ActivityQuery>()
    var outcome: ActivityOutcome = ActivityOutcome.Success(activityFixture())
    var gate: CompletableDeferred<Unit>? = null
    var respondToQuery = false
    override suspend fun getActivity(query: ActivityQuery): ActivityOutcome {
        queries += query
        gate?.await()
        return if (respondToQuery) ActivityOutcome.Success(activityForQuery(query)) else outcome
    }
}
fun activityFixture(empty: Boolean = false): ProviderActivity {
    val end = Instant.parse("2026-10-03T12:00:00Z")
    val start = end.minusSeconds(30 * 86400L)
    return ProviderActivity(ActivityPeriod(start, end, "day", "America/Argentina/Buenos_Aires"), end,
        if (empty) ActivityResults(0, 0, 0, 0, 0, 0, 0, null, "ARS")
        else ActivityResults(8, 5, 3, 4, 3, 1, 12345678, 2469136, "ARS"),
        List(31) { index ->
            val from = if (index == 0) start else start.atZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                .toLocalDate().plusDays(index.toLong()).atStartOfDay(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).toInstant()
            val to = if (index == 30) end else start.atZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                .toLocalDate().plusDays(index + 1L).atStartOfDay(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).toInstant()
            ActivityBucket(from, to, if (!empty && index == 0) 8 else 0,
                if (!empty && index == 0) 5 else 0, if (!empty && index == 0) 3 else 0)
        }, CurrentPending(2, 7, 4))
}

fun activityForQuery(query: ActivityQuery): ProviderActivity {
    val base = activityFixture()
    // The August dataset differs from the recent-work dataset; the adapter selects backend-owned aggregates.
    val results = if (query.from == Instant.parse("2026-08-01T03:00:00Z") &&
        query.to == Instant.parse("2026-09-01T03:00:00Z")) {
        ActivityResults(3, 2, 1, 2, 1, 1, 200000, 100000, "ARS")
    } else base.results
    val zone = java.time.ZoneId.of("America/Argentina/Buenos_Aires")
    val buckets = mutableListOf<ActivityBucket>()
    var cursor = query.from
    while (cursor < query.to) {
        val day = cursor.atZone(zone).toLocalDate()
        val nextDay = when (query.granularity) {
            ActivityGranularity.DAY -> day.plusDays(1)
            ActivityGranularity.WEEK -> day.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY))
            ActivityGranularity.MONTH -> day.withDayOfMonth(1).plusMonths(1)
        }
        val next = minOf(nextDay.atStartOfDay(zone).toInstant(), query.to)
        buckets += ActivityBucket(cursor, next, if (buckets.isEmpty()) results.confirmedBookings else 0,
            if (buckets.isEmpty()) results.reportedCompletions else 0,
            if (buckets.isEmpty()) results.fullyPaidWorkOrders else 0)
        cursor = next
    }
    val period = ActivityPeriod(query.from, query.to, query.granularity.name.lowercase(java.util.Locale.ROOT), zone.id)
    val comparison = if (query.comparePrevious) {
        val previous = ActivityResults(4, 0, 0, 0, 0, 0, 0, null, "ARS")
        ActivityComparison(period.copy(from = query.from.minus(java.time.Duration.between(query.from, query.to)), to = query.from),
            previous, ActivityChanges(ActivityChange(results.confirmedBookings - 4,
                    if (results.confirmedBookings == 3L) -25.0 else 100.0),
                ActivityChange(results.reportedCompletions, null), ActivityChange(results.fullyPaidWorkOrders, null),
                ActivityChange(results.clientsServed, null), ActivityChange(results.newClients, null),
                ActivityChange(results.returningClients, null), ActivityChange(results.agreedValueCents, null),
                ActivityChange(null, null)))
    } else null
    return base.copy(period = period, results = results, evolution = buckets, comparison = comparison)
}
