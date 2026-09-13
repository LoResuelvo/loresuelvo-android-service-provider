package com.loresuelvo.serviceprovider.platform.paymentaccount

data class PaymentAccountConfig(
    val allowedDomains: Set<String> = setOf("mercadopago.com", "mercadopago.com.ar"),
)
