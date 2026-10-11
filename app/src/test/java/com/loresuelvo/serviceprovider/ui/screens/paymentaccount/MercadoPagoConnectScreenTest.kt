package com.loresuelvo.serviceprovider.ui.screens.paymentaccount

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectError
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MercadoPagoConnectScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initial_network_failure_keeps_optional_exit_and_hides_technical_details() {
        var continued = false
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            LoresuelvoTheme {
                MercadoPagoConnectScreen(
                    uiState = MercadoPagoConnectUiState(loading = false, error = MercadoPagoConnectError.Network("private-host: connection refused")),
                    onContinueWithoutConnecting = { continued = true },
                )
            }
        }
        compose.onNodeWithText("private-host: connection refused").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.registration_connection_error)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.mercadopago_continue_without_connecting)).performScrollTo().performClick()
        assertTrue(continued)
    }
}
