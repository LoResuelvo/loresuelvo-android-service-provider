package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.*
import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.Duration
import java.time.OffsetDateTime

internal fun ProviderCollectionsDto.toDomain(): ProviderCollections {
    val effective = period.toCollectionPeriod()
    require(currency == "ARS")
    val amounts = results.toAmounts()
    val buckets = evolution.map { bucket ->
        val from = OffsetDateTime.parse(bucket.from).toInstant()
        val to = OffsetDateTime.parse(bucket.to).toInstant()
        require(from >= effective.from && to <= effective.to && from < to)
        CollectionBucket(from, to, CollectionAmountsDto(bucket.bookingDepositCents,
            bucket.serviceBalanceCents, bucket.totalCents).toAmounts())
    }
    require(buckets.isNotEmpty() && buckets.first().from == effective.from && buckets.last().to == effective.to)
    require(buckets.zipWithNext().all { (a, b) -> a.to == b.from })
    fun sum(selector: (CollectionAmounts) -> Long) = buckets.fold(0L) { sum, bucket ->
        Math.addExact(sum, selector(bucket.amounts))
    }
    require(sum { it.bookingDepositCents } == amounts.bookingDepositCents)
    require(sum { it.serviceBalanceCents } == amounts.serviceBalanceCents)
    val previous = comparison?.let {
        val previousPeriod = it.period.toCollectionPeriod()
        require(previousPeriod.to == effective.from &&
            Duration.between(previousPeriod.from, previousPeriod.to) == Duration.between(effective.from, effective.to))
        require(previousPeriod.granularity == effective.granularity && previousPeriod.timeZone == effective.timeZone)
        CollectionComparison(previousPeriod, it.results.toAmounts(), CollectionChanges(
            it.changes.bookingDepositCents.toCollectionChange(), it.changes.serviceBalanceCents.toCollectionChange(),
            it.changes.totalCents.toCollectionChange()))
    }
    return ProviderCollections(effective, OffsetDateTime.parse(calculatedAt).toInstant(), currency, amounts,
        CurrentCollectionPending(currentPending.scheduled.toPending(), currentPending.awaitingPayment.toPending()),
        buckets, previous)
}

private fun ActivityPeriodDto.toCollectionPeriod(): ActivityPeriod {
    val start = OffsetDateTime.parse(from).toInstant()
    val end = OffsetDateTime.parse(to).toInstant()
    require(start < end && granularity in listOf("day", "week", "month"))
    require(timeZone == "America/Argentina/Buenos_Aires")
    return ActivityPeriod(start, end, granularity, timeZone)
}
private fun CollectionAmountsDto.toAmounts(): CollectionAmounts {
    require(bookingDepositCents >= 0 && serviceBalanceCents >= 0 && totalCents >= 0)
    require(bookingDepositCents <= Long.MAX_VALUE - serviceBalanceCents)
    require(totalCents == bookingDepositCents + serviceBalanceCents)
    return CollectionAmounts(bookingDepositCents, serviceBalanceCents, totalCents)
}
private fun CollectionPendingBalanceDto.toPending(): PendingCollectionBalance {
    require(orders >= 0 && amountCents >= 0)
    return PendingCollectionBalance(orders, amountCents)
}
private fun ActivityChangeDto.toCollectionChange(): ActivityChange {
    require(absolute != null && (percentage == null || percentage.isFinite()))
    return ActivityChange(absolute, percentage)
}
