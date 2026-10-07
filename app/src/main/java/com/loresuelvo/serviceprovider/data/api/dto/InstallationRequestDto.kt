package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class InstallationRequestDto(
    @SerialName("installation_secret") val secret: String,
    val app: String,
    @SerialName("fcm_token") val token: String,
    val locale: String,
    @SerialName("binding_id") val bindingId: String,
    @SerialName("previous_binding_id") val previousBindingId: String? = null,
)

@Serializable
data class RemoveInstallationRequestDto(
    @SerialName("installation_secret") val secret: String,
    @SerialName("binding_id") val bindingId: String,
)
