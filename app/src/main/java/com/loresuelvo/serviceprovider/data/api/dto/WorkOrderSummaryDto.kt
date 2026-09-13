package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkOrderSummaryDto(
    @SerialName("id") val id: Int,
    @SerialName("service_proposal_id") val serviceProposalId: Int,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("scheduled_on") val scheduledOn: String,
    @SerialName("description") val description: String,
    @SerialName("status") val status: String,
    @SerialName("accepted_on") val acceptedOn: String,
    @SerialName("counterpart") val counterpart: WorkOrderCounterpartDto,
)

@Serializable
data class WorkOrderCounterpartDto(
    @SerialName("id") val id: Int,
    @SerialName("role") val role: String,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
)
