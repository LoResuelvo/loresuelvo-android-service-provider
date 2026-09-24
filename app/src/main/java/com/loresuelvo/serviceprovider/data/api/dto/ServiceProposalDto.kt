package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateServiceProposalRequestDto(
    @SerialName("consumer_id") val consumerId: Int,
    val amount: String,
    @SerialName("scheduled_on") val scheduledOn: String,
    val description: String,
    @SerialName("estimated_duration_minutes") val estimatedDurationMinutes: Int,
)

@Serializable
data class ServiceProposalResponseDto(
    val id: Int,
    @SerialName("conversation_id") val conversationId: Int,
    @SerialName("consumer_id") val consumerId: Int,
    @SerialName("provider_id") val providerId: Int,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("scheduled_on") val scheduledOn: String,
    val description: String,
    val status: String,
    @SerialName("estimated_duration_minutes") val estimatedDurationMinutes: Int,
    @SerialName("booking_terms") val bookingTerms: BookingTermsDto,
)

@Serializable
data class BookingTermsDto(
    val currency: String,
    @SerialName("service_total_cents") val serviceTotalCents: Long,
    @SerialName("deposit_cents") val depositCents: Long,
    @SerialName("remaining_service_balance_cents") val remainingServiceBalanceCents: Long,
    @SerialName("platform_fee_total_cents") val platformFeeTotalCents: Long,
    @SerialName("platform_fee_due_now_cents") val platformFeeDueNowCents: Long,
    @SerialName("remaining_platform_fee_cents") val remainingPlatformFeeCents: Long,
    @SerialName("amount_due_now_cents") val amountDueNowCents: Long,
    @SerialName("remaining_amount_due_cents") val remainingAmountDueCents: Long,
    @SerialName("contract_total_cents") val contractTotalCents: Long,
    @SerialName("booking_payment_deadline") val bookingPaymentDeadline: String,
)

@Serializable
data class ServiceProposalErrorDto(val error: String)
