package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AcceptedJobRequestDto(
    @SerialName("id") val id: Int,
    @SerialName("conversation_id") val conversationId: Int,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("status") val status: String,
    @SerialName("images") val images: List<JobRequestImageDto> = emptyList(),
)
