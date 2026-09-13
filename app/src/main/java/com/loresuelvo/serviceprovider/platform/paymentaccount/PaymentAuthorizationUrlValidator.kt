package com.loresuelvo.serviceprovider.platform.paymentaccount

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentAuthorizationUrlValidator @Inject constructor(
    private val config: PaymentAccountConfig,
) {
    fun isValid(url: String): Boolean = runCatching {
        val uri = URI(url)
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "https") return false
        if (uri.userInfo != null) return false
        val host = uri.host?.lowercase() ?: return false
        config.allowedDomains.any { domain ->
            val normalizedDomain = domain.lowercase()
            host == normalizedDomain || host.endsWith(".$normalizedDomain")
        }
    }.getOrDefault(false)
}
