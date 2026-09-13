package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentAccountAuthorizationDto(
    @SerialName("authorization_url") val authorizationUrl: String,
    @SerialName("state") val state: String,
)
