package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WorkOrderDetailDto(
    @SerialName("id") val id: Int,
    @SerialName("service_proposal_id") val serviceProposalId: Int,
    @SerialName("consumer_id") val consumerId: Int,
    @SerialName("provider_id") val providerId: Int,
    @SerialName("amount_cents") val amountCents: Long,
    @SerialName("scheduled_on") val scheduledOn: String,
    @SerialName("description") val description: String,
    @SerialName("status") val status: String,
    @SerialName("accepted_on") val acceptedOn: String,
    @SerialName("paid_on") val paidOn: String? = null,
    @SerialName("completion_report") val completionReport: WorkOrderCompletionReportDto? = null,
    @SerialName("review") val review: WorkOrderReviewDto? = null,
)

@Serializable
data class WorkOrderCompletionReportDto(
    @SerialName("id") val id: Int,
    @SerialName("description") val description: String,
    @SerialName("reported_on") val reportedOn: String,
    @SerialName("images") val images: List<WorkOrderCompletionImageDto>,
)

@Serializable
data class WorkOrderCompletionImageDto(
    @SerialName("file_id") val fileId: String,
    @SerialName("original_name") val originalName: String,
    @SerialName("url") val url: String,
)

@Serializable
data class WorkOrderReviewDto(
    @SerialName("rating") val rating: Int,
    @SerialName("description") val description: String,
)
