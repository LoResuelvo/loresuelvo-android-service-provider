package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderCollectionsDto(val period: ActivityPeriodDto,
    @SerialName("calculated_at") val calculatedAt: String, val currency: String,
    val results: CollectionAmountsDto, val evolution: List<CollectionBucketDto>,
    @SerialName("current_pending") val currentPending: CollectionPendingDto,
    val comparison: CollectionComparisonDto? = null)
@Serializable
data class CollectionAmountsDto(
    @SerialName("booking_deposit_cents") val bookingDepositCents: Long,
    @SerialName("service_balance_cents") val serviceBalanceCents: Long,
    @SerialName("total_cents") val totalCents: Long)
@Serializable
data class CollectionBucketDto(val from: String, val to: String,
    @SerialName("booking_deposit_cents") val bookingDepositCents: Long,
    @SerialName("service_balance_cents") val serviceBalanceCents: Long,
    @SerialName("total_cents") val totalCents: Long)
@Serializable
data class CollectionPendingBalanceDto(val orders: Long, @SerialName("amount_cents") val amountCents: Long)
@Serializable
data class CollectionPendingDto(val scheduled: CollectionPendingBalanceDto,
    @SerialName("awaiting_payment") val awaitingPayment: CollectionPendingBalanceDto)
@Serializable
data class CollectionComparisonDto(val period: ActivityPeriodDto, val results: CollectionAmountsDto,
    val changes: CollectionChangesDto)
@Serializable
data class CollectionChangesDto(
    @SerialName("booking_deposit_cents") val bookingDepositCents: ActivityChangeDto,
    @SerialName("service_balance_cents") val serviceBalanceCents: ActivityChangeDto,
    @SerialName("total_cents") val totalCents: ActivityChangeDto)
