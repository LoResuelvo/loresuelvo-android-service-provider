package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JobRequestSummaryDto(
    @SerialName("id") val id: Int,
    @SerialName("conversation_id") val conversationId: Int,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("status") val status: String,
    @SerialName("requester") val requester: JobRequestRequesterDto,
    @SerialName("images") val images: List<JobRequestImageDto> = emptyList(),
)

@Serializable
data class JobRequestRequesterDto(
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
)

@Serializable
data class JobRequestImageDto(
    @SerialName("id") val id: String,
    @SerialName("url") val url: String,
    @SerialName("original_name") val originalName: String,
)
