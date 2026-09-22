package com.loresuelvo.serviceprovider.platform.paymentaccount

data class PaymentAccountConfig(
    val allowedDomains: Set<String> = setOf("mercadopago.com", "mercadopago.com.ar"),
    val returnHost: String = "",
    val returnPath: String = DEFAULT_RETURN_PATH,
    val allowedSchemes: Set<String> = setOf(HTTPS_SCHEME),
) {
    companion object {
        const val HTTPS_SCHEME = "https"
        const val DEFAULT_RETURN_PATH = "/provider/register/mercado-pago"
        const val RESULT_PARAM = "result"
        const val RESULT_SUCCESS = "success"
        const val RESULT_CANCELLED = "cancelled"
    }
}
