package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasClickAction
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import java.time.Instant
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR")
class ProviderTurnsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val originalZone = TimeZone.getDefault()

    @Before fun setUp() { TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires")) }
    @After fun tearDown() { TimeZone.setDefault(originalZone) }

    @Test fun shows_real_local_data_and_initials_without_category_space() {
        showOrder(null)

        compose.onNodeWithTag("provider_turn_7").assertExists()
        compose.onNodeWithText("Ana Pérez").assertExists()
        compose.onNodeWithText("AP").assertExists()
        compose.onNodeWithText("Reparar la canilla").assertExists()
        compose.onNodeWithText("ARS 15.000,50").assertExists()
        compose.onNodeWithText("el 4 de octubre a las 21:30").assertExists()
    }

    @Test fun falls_back_to_initials_when_photo_cannot_load() {
        showOrder("invalid://photo")

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("provider_avatar_photo_error").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("AP").assertExists()
    }

    @Test fun shows_published_statuses_for_past_orders_without_inferring_unsupported_status() {
        val past = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()
        val orders = listOf(
            WorkOrder(1, "Ana", "Work", past, WorkOrderStatus.Scheduled),
            WorkOrder(2, "Bea", "Work", past, WorkOrderStatus.AwaitingPayment),
            WorkOrder(3, "Cora", "Work", past, WorkOrderStatus.Paid),
            WorkOrder(4, "Dani", "Work", past, WorkOrderStatus.Unsupported("unknown")),
        )
        compose.setContent { LoresuelvoTheme { ProviderTurnsScreen(ProviderTurnsUiState.Ready(orders), {}, {}) } }

        compose.onNodeWithText("Confirmado").assertExists()
        compose.onNodeWithTag("provider_turns_list").performScrollToIndex(1)
        compose.onNodeWithText("Pendiente de pago").assertExists()
        compose.onNodeWithTag("provider_turns_list").performScrollToIndex(2)
        compose.onNodeWithText("Pagado").assertExists()
        compose.onNodeWithTag("provider_turns_list").performScrollToIndex(3)
        compose.onAllNodesWithTag("provider_turn_status_4").fetchSemanticsNodes().isEmpty().let { org.junit.Assert.assertTrue(it) }
    }

    @Test fun details_action_only_opens_summary() {
        val order = WorkOrder(7, "Ana Pérez", "A long reason that must remain fully visible in detail",
            Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(), WorkOrderStatus.Scheduled,
            amountCents = 1500050)
        var selected: WorkOrder? = null
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), {}, {}, { selected = it })
        } }
        compose.onNodeWithTag("provider_turn_7").performClick()
        org.junit.Assert.assertNull(selected)
        compose.onNodeWithTag("provider_turn_details_7").performClick()
        org.junit.Assert.assertEquals(order, selected)
    }

    @Test fun full_summary_shows_loaded_order_and_only_conversation_action() {
        val order = WorkOrder(7, "Ana Pérez", "A long reason that must remain fully visible in detail",
            Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(), WorkOrderStatus.Scheduled,
            amountCents = 1500050)
        var conversations = 0
        compose.setContent { LoresuelvoTheme {
            ProviderTurnDetailScreen(order, {}, { conversations++ })
        } }
        compose.onNodeWithText(order.description).assertExists()
        compose.onNodeWithText("Ana Pérez").assertExists()
        compose.onNodeWithText("ARS 15.000,50").assertExists()
        compose.onNodeWithText("el 4 de octubre a las 21:30").assertExists()
        compose.onNodeWithText("Confirmado").assertExists()
        org.junit.Assert.assertEquals(2, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        compose.onNodeWithTag("provider_turn_conversation").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1, conversations)
    }

    @Test fun details_back_returns_to_loaded_list_without_refetch() {
        val order = WorkOrder(7, "Ana Pérez", "Complete reason", 1, WorkOrderStatus.Scheduled)
        var retries = 0
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), {}, { retries++ })
        } }
        compose.onNodeWithTag("provider_turn_details_7").performClick()
        compose.onNodeWithText("Detalle del turno").assertExists()
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithTag("provider_turn_7").assertExists()
        org.junit.Assert.assertEquals(0, retries)
    }


    private fun showOrder(photo: String?) {
        val order = WorkOrder(
            id = 7, consumerName = "Ana Pérez", description = "Reparar la canilla",
            scheduledOn = Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(),
            status = WorkOrderStatus.Scheduled, amountCents = 1500050,
            consumerGivenName = "Ana", consumerSurname = "Pérez", consumerPhotoUrl = photo,
        )
        compose.setContent {
            LoresuelvoTheme {
                ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), {}, {})
            }
        }
    }
}
