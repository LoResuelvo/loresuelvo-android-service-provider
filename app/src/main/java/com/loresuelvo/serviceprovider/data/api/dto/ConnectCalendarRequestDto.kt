package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ConnectCalendarRequestDto(
    @SerialName("server_auth_code") val serverAuthCode: String,
)
