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

    @Test fun profile_return_uses_an_arrow_for_pending_and_connected_accounts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val state = androidx.compose.runtime.mutableStateOf(MercadoPagoConnectUiState(
            accountStatus = com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus(
                com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus.PENDING)))
        var pendingReturns = 0
        var connectedReturns = 0
        compose.setContent { LoresuelvoTheme {
            MercadoPagoConnectScreen(state.value, returnToProfile = true,
                onContinueWithoutConnecting = { pendingReturns++ }, onContinueHome = { connectedReturns++ })
        } }
        val label = context.getString(R.string.mercadopago_return_profile)
        compose.onNodeWithContentDescription(label).assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals(1, pendingReturns)
        compose.runOnIdle { state.value = state.value.copy(accountStatus =
            com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus(
                com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus.CONNECTED)) }
        compose.onNodeWithContentDescription(label).performClick()
        org.junit.Assert.assertEquals(1, connectedReturns)
        compose.onNodeWithText(label).assertDoesNotExist()
    }

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
