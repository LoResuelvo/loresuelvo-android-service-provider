package com.loresuelvo.serviceprovider

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNav
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.platform.auth.BrowserAuthenticationLauncher
import com.loresuelvo.serviceprovider.platform.identity.IdentityVerificationLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountBrowserLauncher
import com.loresuelvo.serviceprovider.platform.paymentaccount.PaymentAccountReturnLinkParser
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single-Activity host. `@AndroidEntryPoint` is required so Hilt
 * can resolve `@HiltViewModel` consumers inside the navigation
 * graph (Welcome's ViewModel is the first one injected in the
 * process). Without the annotation the first `hiltViewModel()` call
 * crashes with `IllegalStateException: Given component holder class
 * MainActivity does not implement interface
 * dagger.hilt.internal.GeneratedComponent`.
 *
 * `onCreate` is intentionally minimal: composition root lives in
 * [LoResuelvoNav] and theme wrapping lives in [LoresuelvoTheme].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var browserAuthenticationLauncher: BrowserAuthenticationLauncher
    @Inject lateinit var identityVerificationLauncher: IdentityVerificationLauncher
    @Inject lateinit var paymentAccountBrowserLauncher: PaymentAccountBrowserLauncher
    @Inject lateinit var paymentAccountReturnLinkParser: PaymentAccountReturnLinkParser

    private val paymentReturnUrl = MutableStateFlow<String?>(null)
    private var paymentReturnConsumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paymentReturnConsumed = savedInstanceState?.getBoolean(PAYMENT_RETURN_CONSUMED) ?: false
        if (!paymentReturnConsumed) paymentReturnUrl.value = intent?.dataString
        setContent {
            LoresuelvoTheme {
                LoResuelvoNav(
                    browserAuthenticationLauncher = browserAuthenticationLauncher,
                    identityVerificationLauncher = identityVerificationLauncher,
                    paymentAccountBrowserLauncher = paymentAccountBrowserLauncher,
                    paymentReturnLinkParser = paymentAccountReturnLinkParser,
                    paymentReturnUrl = paymentReturnUrl,
                    onPaymentReturnConsumed = ::consumePaymentReturn,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acceptPaymentReturn(intent)
    }

    internal fun acceptPaymentReturn(intent: Intent) {
        paymentReturnConsumed = false
        paymentReturnUrl.value = intent.dataString
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PAYMENT_RETURN_CONSUMED, paymentReturnConsumed)
        super.onSaveInstanceState(outState)
    }

    private fun consumePaymentReturn() {
        paymentReturnConsumed = true
        paymentReturnUrl.value = null
    }

    private companion object {
        const val PAYMENT_RETURN_CONSUMED = "payment-return-consumed"
    }
}
