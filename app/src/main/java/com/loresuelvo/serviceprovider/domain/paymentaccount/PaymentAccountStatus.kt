package com.loresuelvo.serviceprovider.domain.paymentaccount

enum class ConnectionStatus {
    PENDING,
    CONNECTED,
}

data class PaymentAccountStatus(
    val status: ConnectionStatus,
    val accountId: String? = null,
    val canReceivePayments: Boolean = false,
    val canSendServiceProposals: Boolean = false,
)
