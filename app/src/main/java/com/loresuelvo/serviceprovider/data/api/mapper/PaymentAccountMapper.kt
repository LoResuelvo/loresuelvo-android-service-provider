package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.PaymentAccountStatusDto
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus

fun PaymentAccountStatusDto.toDomain(): PaymentAccountStatus {
    val connectionStatus = when (status.lowercase()) {
        "connected" -> ConnectionStatus.CONNECTED
        else -> ConnectionStatus.PENDING
    }
    return PaymentAccountStatus(
        status = connectionStatus,
        accountId = accountId,
        canReceivePayments = canReceivePayments,
        canSendServiceProposals = canSendServiceProposals,
    )
}
