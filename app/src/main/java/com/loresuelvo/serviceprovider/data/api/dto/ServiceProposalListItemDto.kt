package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceProposalListItemDto(
    val id: Int,
    @SerialName("conversation_id") val conversationId: Int,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("scheduled_on") val scheduledOn: String,
    val description: String,
    @SerialName("estimated_duration_minutes") val estimatedDurationMinutes: Int,
    val status: String,
    @SerialName("created_on") val createdOn: String,
    val counterpart: ServiceProposalCounterpartDto,
    @SerialName("booking_terms") val bookingTerms: BookingTermsDto,
)

@Serializable
data class ServiceProposalCounterpartDto(
    val id: Int,
    val role: String,
    val name: String,
    val surname: String,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
)
