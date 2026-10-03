package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.*
import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.OffsetDateTime

internal fun ProviderActivityDto.toDomain(): ProviderActivity {
    fun instant(value: String) = OffsetDateTime.parse(value).toInstant()
    val start = instant(period.from)
    val end = instant(period.to)
    require(start < end && period.granularity in listOf("day", "week", "month"))
    require(period.timeZone == "America/Argentina/Buenos_Aires")
    results.validate()
    require(listOf(currentPending.requests, currentPending.scheduledOrders,
        currentPending.awaitingPaymentOrders).all { it >= 0 })
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
        CurrentPending(currentPending.requests, currentPending.scheduledOrders, currentPending.awaitingPaymentOrders),
        comparison?.let { previous ->
            val previousStart = instant(previous.period.from)
            val previousEnd = instant(previous.period.to)
            require(previousStart < previousEnd && previousEnd == start)
            require(java.time.Duration.between(previousStart, previousEnd) == java.time.Duration.between(start, end))
            require(previous.period.granularity == period.granularity && previous.period.timeZone == period.timeZone)
            ActivityComparison(ActivityPeriod(previousStart, previousEnd, previous.period.granularity, previous.period.timeZone),
                previous.results.toResults(), previous.changes.toChanges())
        })
}

private fun ActivityResultsDto.toResults(): ActivityResults {
    validate()
    return ActivityResults(confirmedBookings, reportedCompletions, fullyPaidWorkOrders, clientsServed,
        newClients, returningClients, agreedValueCents, averageValueCents, currency)
}

private fun ActivityChangeDto.toChange(nullable: Boolean = false): ActivityChange {
    require(nullable || absolute != null)
    require(percentage == null || percentage.isFinite())
    return ActivityChange(absolute, percentage)
}

private fun ActivityChangesDto.toChanges() = ActivityChanges(confirmedBookings.toChange(),
    reportedCompletions.toChange(), fullyPaidWorkOrders.toChange(), clientsServed.toChange(),
    newClients.toChange(), returningClients.toChange(), agreedValueCents.toChange(), averageValueCents.toChange(true))

private fun ActivityResultsDto.validate() {
    require(currency == "ARS")
    require(listOf(confirmedBookings, reportedCompletions, fullyPaidWorkOrders,
        clientsServed, newClients, returningClients, agreedValueCents).all { it >= 0 })
    require(averageValueCents == null || averageValueCents >= 0)
    require(newClients <= clientsServed &&
        returningClients == clientsServed - newClients)
    if (reportedCompletions == 0L) {
        require(agreedValueCents == 0L && averageValueCents == null)
    } else {
        val expectedAverage = java.math.BigDecimal.valueOf(agreedValueCents)
            .divide(java.math.BigDecimal.valueOf(reportedCompletions), 0, java.math.RoundingMode.HALF_UP)
            .longValueExact()
        require(averageValueCents == expectedAverage)
    }
}
