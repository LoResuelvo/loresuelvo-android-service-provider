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
        val host = uri.host?.lowercase() ?: return null
        if (config.returnHost.isBlank() || host != config.returnHost.lowercase()) return null
        if (uri.userInfo != null || uri.port != -1 || uri.fragment != null) return null
        if (uri.path != config.returnPath) return null

        val results = uri.rawQuery?.split("&")?.mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts[0] == PaymentAccountConfig.RESULT_PARAM) parts.getOrNull(1) else null
        } ?: return null
        if (results.size != 1) return null

        when (results.single()) {
            PaymentAccountConfig.RESULT_SUCCESS -> PaymentAccountReturnHint.Success
            PaymentAccountConfig.RESULT_CANCELLED -> PaymentAccountReturnHint.Cancelled
            else -> null
        }
    }.getOrNull()
}
