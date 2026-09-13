package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentAccountStatusDto(
    @SerialName("status") val status: String,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("can_receive_payments") val canReceivePayments: Boolean = false,
    @SerialName("can_send_service_proposals") val canSendServiceProposals: Boolean = false,
)
