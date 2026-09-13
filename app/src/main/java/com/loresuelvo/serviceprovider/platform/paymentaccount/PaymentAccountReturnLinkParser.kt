package com.loresuelvo.serviceprovider.platform.paymentaccount

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PaymentAccountReturnHint {
    data object Success : PaymentAccountReturnHint
    data object Cancelled : PaymentAccountReturnHint
}

@Singleton
class PaymentAccountReturnLinkParser @Inject constructor(
    private val config: PaymentAccountConfig,
) {
    constructor() : this(PaymentAccountConfig())

    fun parse(url: String): PaymentAccountReturnHint? = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (!config.allowedSchemes.contains(scheme)) return null
        if (uri.path != config.returnPath) return null

        val query = uri.query ?: return null
        val queryParams = query.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        }

        when (queryParams[PaymentAccountConfig.RESULT_PARAM]) {
            PaymentAccountConfig.RESULT_SUCCESS -> PaymentAccountReturnHint.Success
            PaymentAccountConfig.RESULT_CANCELLED -> PaymentAccountReturnHint.Cancelled
            else -> null
        }
    }.getOrNull()
}
