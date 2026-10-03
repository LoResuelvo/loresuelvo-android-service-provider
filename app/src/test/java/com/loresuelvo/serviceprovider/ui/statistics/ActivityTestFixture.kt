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
    override suspend fun getActivity(query: ActivityQuery): ActivityOutcome {
        queries += query
        gate?.await()
        return outcome
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
