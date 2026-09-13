package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrentAccountDto(
    @SerialName("id") val id: Int,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("email") val email: String,
    @SerialName("role") val role: String,
    @SerialName("profile_photo") val profilePhoto: CurrentAccountProfilePhotoDto? = null,
    @SerialName("category") val category: CategoryDto? = null,
)

@Serializable
data class CurrentAccountProfilePhotoDto(
    @SerialName("original_name") val originalName: String,
    @SerialName("url") val url: String,
)
