package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR")
class ProviderCompletionFormScreenTest {
    @get:Rule val compose = createComposeRule()
    private val order = WorkOrder(7, "Ana Pérez", "Reparar la canilla", 1, WorkOrderStatus.Scheduled)

    @Test fun eligible_order_shows_identity_and_empty_form_without_submission() {
        var backs = 0
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionFormScreen(order, CompletionFormAvailability.Eligible, "", {}, {}, { backs++ })
        } }

        compose.onNodeWithText("Ana Pérez").assertExists()
        compose.onNodeWithText("Reparar la canilla").assertExists()
        compose.onNodeWithText("Descripción de la entrega").assertExists()
        compose.onNodeWithText("Agregar fotos").assertExists()
        compose.onNodeWithTag("completion_submit").assertIsNotEnabled()
        compose.onNodeWithText("Volver").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun checking_and_ineligible_orders_do_not_show_form() {
        var availability by mutableStateOf(CompletionFormAvailability.Checking)
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionFormScreen(order, availability, "", {}, {}, {})
        } }
        val explanations = mapOf(
            CompletionFormAvailability.Checking to "Consultando el estado de la orden…",
            CompletionFormAvailability.TooEarly to "Todavía no podés informar la finalización de este turno.",
            CompletionFormAvailability.AlreadyReported to "Esta orden ya tiene un reporte de finalización.",
            CompletionFormAvailability.Forbidden to "No tenés permiso para informar la finalización de esta orden.",
        )
        for ((state, explanation) in explanations) {
            compose.runOnIdle { availability = state }
            compose.onNodeWithText(explanation).assertExists()
            compose.onNodeWithText("Descripción de la entrega").assertDoesNotExist()
            compose.onNodeWithTag("completion_submit").assertDoesNotExist()
        }
    }
}
