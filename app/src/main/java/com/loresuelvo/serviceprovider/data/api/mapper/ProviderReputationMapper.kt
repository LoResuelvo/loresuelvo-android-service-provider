package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.ProviderReputationDto
import com.loresuelvo.serviceprovider.domain.statistics.ProviderReputation
import com.loresuelvo.serviceprovider.domain.statistics.RatingCount
import com.loresuelvo.serviceprovider.domain.statistics.ReceivedReview
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal fun ProviderReputationDto.toDomain(): ProviderReputation {
    val calculated = OffsetDateTime.parse(calculatedAt)
    require(calculated.offset == ZoneOffset.UTC)
    require(reviewCount >= 0 && eligiblePaidOrders >= 0 && reviewedPaidOrders >= 0)
    require(reviewCount == reviewedPaidOrders && reviewedPaidOrders <= eligiblePaidOrders)
    require(ratingDistribution.map { it.rating } == (1..5).toList())
    require(ratingDistribution.all { it.count >= 0 })
    require(ratingDistribution.fold(0L) { sum, bucket -> Math.addExact(sum, bucket.count) } == reviewCount)
    require(if (reviewCount == 0L) averageRating == null else
        averageRating != null && averageRating.isFinite() && averageRating in 1.0..5.0)
    require(if (eligiblePaidOrders == 0L) coveragePercentage == null else
        coveragePercentage != null && coveragePercentage.isFinite() && coveragePercentage in 0.0..100.0)
    require(reviewCount != 0L || eligiblePaidOrders == 0L || coveragePercentage == 0.0)
    require(reviews.size <= 20 && reviews.size.toLong() <= reviewCount)
    require(reviews.all { it.workOrderId > 0 && it.rating in 1..5 && it.description.length <= 500 })
    require(reviews.zipWithNext().all { (first, second) -> first.workOrderId > second.workOrderId })
    require(nextCursor == null || nextCursor.length in 1..4096)
    require(reviewCount != 0L || (reviews.isEmpty() && nextCursor == null))
    return ProviderReputation(calculated.toInstant(), averageRating, reviewCount,
        ratingDistribution.map { RatingCount(it.rating, it.count) }, eligiblePaidOrders, reviewedPaidOrders,
        coveragePercentage, reviews.map { ReceivedReview(it.workOrderId, it.rating, it.description) }, nextCursor)
}
