package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.ProviderActivityDto
import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.OffsetDateTime

internal fun ProviderActivityDto.toDomain(): ProviderActivity {
    fun instant(value: String) = OffsetDateTime.parse(value).toInstant()
    val start = instant(period.from)
    val end = instant(period.to)
    require(start < end && period.granularity in listOf("day", "week", "month"))
    require(period.timeZone == "America/Argentina/Buenos_Aires")
    require(results.currency == "ARS")
    require(listOf(results.confirmedBookings, results.reportedCompletions, results.fullyPaidWorkOrders,
        results.clientsServed, results.newClients, results.returningClients, results.agreedValueCents,
        currentPending.requests, currentPending.scheduledOrders, currentPending.awaitingPaymentOrders).all { it >= 0 })
    require(results.averageValueCents == null || results.averageValueCents >= 0)
    require(results.newClients <= results.clientsServed &&
        results.returningClients == results.clientsServed - results.newClients)
    if (results.reportedCompletions == 0L) {
        require(results.agreedValueCents == 0L && results.averageValueCents == null)
    } else {
        val expectedAverage = java.math.BigDecimal.valueOf(results.agreedValueCents)
            .divide(java.math.BigDecimal.valueOf(results.reportedCompletions), 0, java.math.RoundingMode.HALF_UP)
            .longValueExact()
        require(results.averageValueCents == expectedAverage)
    }
    val buckets = evolution.map {
        val from = instant(it.from)
        val to = instant(it.to)
        require(from >= start && to <= end && from < to)
        require(listOf(it.confirmedBookings, it.reportedCompletions, it.fullyPaidWorkOrders).all { count -> count >= 0 })
        ActivityBucket(from, to, it.confirmedBookings, it.reportedCompletions, it.fullyPaidWorkOrders)
    }
    require(buckets.isNotEmpty() && buckets.first().from == start && buckets.last().to == end)
    require(buckets.zipWithNext().all { (a, b) -> a.to == b.from })
    return ProviderActivity(ActivityPeriod(start, end, period.granularity, period.timeZone), instant(calculatedAt),
        ActivityResults(results.confirmedBookings, results.reportedCompletions, results.fullyPaidWorkOrders,
            results.clientsServed, results.newClients, results.returningClients, results.agreedValueCents,
            results.averageValueCents, results.currency), buckets,
        CurrentPending(currentPending.requests, currentPending.scheduledOrders, currentPending.awaitingPaymentOrders))
}
