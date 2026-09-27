package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostCompletionReportRequestDto(
    @SerialName("description") val description: String,
    @SerialName("image_file_ids") val imageFileIds: List<String>,
)
