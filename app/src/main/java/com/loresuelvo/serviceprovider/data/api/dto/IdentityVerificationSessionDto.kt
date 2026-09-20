package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IdentityVerificationSessionDto(
    @SerialName("session_id") val sessionId: String,
    @SerialName("session_token") val sessionToken: String,
    @SerialName("verification_url") val verificationUrl: String,
    @SerialName("status") val status: String,
)
