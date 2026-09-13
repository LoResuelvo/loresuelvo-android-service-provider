package com.loresuelvo.serviceprovider.ui.paymentaccount

import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus

sealed interface MercadoPagoConnectError {
    data class Network(val message: String) : MercadoPagoConnectError
    data class Server(val code: Int, val message: String?) : MercadoPagoConnectError
    data object Unauthorized : MercadoPagoConnectError
}

data class MercadoPagoConnectUiState(
    val loading: Boolean = false,
    val isUnauthenticated: Boolean = false,
    val isIneligible: Boolean = false,
    val ineligibleOrientation: String? = null,
    val accountStatus: PaymentAccountStatus? = null,
    val error: MercadoPagoConnectError? = null,
) {
    val offersConnection: Boolean
        get() = !loading && !isIneligible && !isUnauthenticated && accountStatus?.status == ConnectionStatus.PENDING

    val offersContinueWithoutConnecting: Boolean
        get() = !loading && !isIneligible && !isUnauthenticated && accountStatus?.status == ConnectionStatus.PENDING
}
