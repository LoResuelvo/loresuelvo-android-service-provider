package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderReputationDto(
    @SerialName("calculated_at") val calculatedAt: String,
    @SerialName("average_rating") val averageRating: Double?,
    @SerialName("review_count") val reviewCount: Long,
    @SerialName("rating_distribution") val ratingDistribution: List<RatingCountDto>,
    @SerialName("eligible_paid_orders") val eligiblePaidOrders: Long,
    @SerialName("reviewed_paid_orders") val reviewedPaidOrders: Long,
    @SerialName("coverage_percentage") val coveragePercentage: Double?,
    val reviews: List<ReceivedReviewDto>,
    @SerialName("next_cursor") val nextCursor: String?,
)
@Serializable
data class RatingCountDto(val rating: Int, val count: Long)
@Serializable
data class ReceivedReviewDto(@SerialName("work_order_id") val workOrderId: Int, val rating: Int,
    val description: String)
