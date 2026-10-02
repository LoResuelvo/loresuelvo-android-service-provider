package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Text/images retain content; audio omits content and all other attachment fields. */
@Serializable
data class SendMessageRequestDto(
    @SerialName("content") val content: String? = null,
    @SerialName("image_file_ids") val imageFileIds: List<String>? = null,
    @SerialName("audio_file_id") val audioFileId: String? = null,
)
