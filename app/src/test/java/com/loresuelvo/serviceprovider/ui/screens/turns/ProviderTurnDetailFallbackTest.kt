package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR")
class ProviderTurnDetailFallbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun network_error_offers_retry_without_previous_order_content() {
        compose.setContent { LoresuelvoTheme { DetailFallback({},
            failure = WorkOrderDetailOutcome.Failure.Network(Exception()), onRetry = {}) } }
        compose.onNodeWithText("No pudimos conectar para cargar la orden.").assertExists()
        compose.onNodeWithText("Reintentar").assertExists()
        compose.onNodeWithText("Old private order").assertDoesNotExist()
    }

    @Test fun forbidden_error_has_exit_without_retry() {
        compose.setContent { LoresuelvoTheme { DetailFallback({},
            failure = WorkOrderDetailOutcome.Failure.Forbidden, onRetry = {}) } }
        compose.onNodeWithText("No tenés permiso para ver esta orden.").assertExists()
        compose.onNodeWithText("Volver").assertExists()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }

    @Test fun missing_order_has_exit_without_retry() {
        compose.setContent { LoresuelvoTheme { DetailFallback({},
            failure = WorkOrderDetailOutcome.Failure.NotFound, onRetry = {}) } }
        compose.onNodeWithText("Esta orden ya no está disponible.").assertExists()
        compose.onNodeWithText("Volver").assertExists()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }
}
