package com.loresuelvo.serviceprovider.ui.paymentaccount

sealed interface MercadoPagoConnectEffect {
    data object NavigateToWelcome : MercadoPagoConnectEffect
    data object NavigateToHome : MercadoPagoConnectEffect
}
