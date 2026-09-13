package com.loresuelvo.serviceprovider.platform.paymentaccount

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

fun interface PaymentAccountBrowserLauncher {
    fun launch(context: Context, url: String): Boolean
}

@Singleton
class ExternalPaymentAccountBrowserLauncher @Inject constructor(
    private val urlValidator: PaymentAuthorizationUrlValidator,
) : PaymentAccountBrowserLauncher {

    constructor() : this(PaymentAuthorizationUrlValidator(PaymentAccountConfig()))

    override fun launch(context: Context, url: String): Boolean {
        if (!urlValidator.isValid(url)) return false

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }
}
