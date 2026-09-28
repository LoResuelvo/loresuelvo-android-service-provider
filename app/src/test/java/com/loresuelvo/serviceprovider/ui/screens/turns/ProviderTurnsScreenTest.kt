package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
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

    @Test fun loading_shows_accessible_progress_and_text_without_empty_message() {
        compose.setContent { LoresuelvoTheme { ProviderTurnsScreen(ProviderTurnsUiState.Loading, {}, {}) } }
        compose.onNodeWithTag("provider_turns_loading").assertExists()
        compose.onNodeWithText("Cargando turnos…").assertExists()
        compose.onNodeWithText("No tenés turnos todavía.").assertDoesNotExist()
    }

    @Test fun empty_orders_show_provider_message_without_loading_or_error() {
        compose.setContent { LoresuelvoTheme { ProviderTurnsScreen(ProviderTurnsUiState.Ready(emptyList()), {}, {}) } }
        compose.onNodeWithTag("provider_turns_empty").assertExists()
        compose.onNodeWithText("Cuando se agende un trabajo, lo vas a ver acá.").assertExists()
        compose.onNodeWithTag("provider_turns_loading").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }

    @Test fun error_shows_retry_and_clears_when_orders_arrive() {
        var retries = 0
        var state: ProviderTurnsUiState by androidx.compose.runtime.mutableStateOf(ProviderTurnsUiState.Error)
        compose.setContent { LoresuelvoTheme { ProviderTurnsScreen(state, {}, {
            retries++
            state = ProviderTurnsUiState.Ready(listOf(WorkOrder(8, "Ana", "Work", 1, WorkOrderStatus.Scheduled)))
        }) } }
        compose.onNodeWithTag("provider_turns_error").assertExists()
        compose.onNodeWithText("No pudimos cargar tus turnos.").assertExists()
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithTag("provider_turn_8").assertExists()
        compose.onNodeWithTag("provider_turns_error").assertDoesNotExist()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, retries) }
    }

    @Test @Config(qualifiers = "en") fun english_error_uses_english_retry_action() {
        compose.setContent { LoresuelvoTheme { ProviderTurnsScreen(ProviderTurnsUiState.Error, {}, {}) } }
        compose.onNodeWithText("We couldn’t load your appointments.").assertExists()
        compose.onNodeWithText("Retry").assertExists()
    }

    @Test fun shows_real_local_data_and_initials_without_category_space() {
        showOrder(null)

        compose.onNodeWithTag("provider_turn_7").assertExists()
        compose.onNodeWithText("Ana Pérez").assertExists()
        compose.onNodeWithText("AP").assertExists()
        compose.onNodeWithText("Reparar la canilla").assertExists()
        compose.onNodeWithText("ARS 15.000,50").assertExists()
        compose.onNodeWithText("el 4 de octubre a las 21:30").assertExists()
    }

    @Test fun enlarged_font_keeps_date_and_details_action_visible() {
        val order = WorkOrder(7, "Ana Pérez", "Reparar la canilla", 1, WorkOrderStatus.Scheduled)
        compose.setContent {
            val enlarged = Configuration(LocalConfiguration.current).apply { fontScale = 1.5f }
            androidx.compose.runtime.CompositionLocalProvider(LocalConfiguration provides enlarged) {
                LoresuelvoTheme { ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), {}, {}) }
            }
        }
        compose.onNodeWithTag("provider_turn_details_7").assertIsDisplayed()
        compose.onNodeWithTag("provider_turn_7").assertIsDisplayed()
    }

    @Test fun enlarged_error_keeps_retry_reachable() {
        var retries = 0
        compose.setContent {
            val enlarged = Configuration(LocalConfiguration.current).apply { fontScale = 1.5f }
            androidx.compose.runtime.CompositionLocalProvider(LocalConfiguration provides enlarged) {
                LoresuelvoTheme { ProviderTurnsScreen(ProviderTurnsUiState.Error, {}, { retries++ }) }
            }
        }
        compose.onNodeWithText("Reintentar").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, retries) }
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

    @Test fun details_action_dispatches_selected_order_for_navigation() {
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

    @Test fun detail_uses_consumer_initials_when_no_photo_is_available() {
        val order = WorkOrder(7, "Ana Pérez", "Full reason", 1, WorkOrderStatus.Scheduled,
            consumerGivenName = "Ana", consumerSurname = "Pérez")
        compose.setContent { LoresuelvoTheme { ProviderTurnDetailScreen(order, {}, {}) } }

        compose.onNodeWithText("AP").assertExists()
        compose.onNodeWithText("Ana Pérez").assertExists()
    }

    @Test fun current_scheduled_detail_shows_exact_amount_and_no_consumer_actions() {
        val order = WorkOrder(42, "Ana Pérez", "Repair the kitchen tap and preserve the fittings",
            Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(), WorkOrderStatus.Scheduled,
            amountCents = 123456, consumerGivenName = "Ana", consumerSurname = "Pérez")
        compose.setContent { LoresuelvoTheme { ProviderTurnDetailScreen(order, {}, {}) } }

        compose.onNodeWithText("ARS 1.234,56").assertExists()
        compose.onNodeWithText("el 4 de octubre a las 21:30").assertExists()
        compose.onNodeWithText(order.description).assertExists()
        compose.onNodeWithText("Confirmado").assertExists()
        compose.onNodeWithText("Pagar").assertDoesNotExist()
        compose.onNodeWithText("Escribir reseña").assertDoesNotExist()
    }

    @Test fun completion_action_uses_selected_real_order_from_turns_list() {
        val order = WorkOrder(7, "Ana Pérez", "Full reason", 1, WorkOrderStatus.Scheduled)
        var openedId: Int? = null
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), {}, {},
                onCompletion = { openedId = it })
        } }

        compose.onNodeWithTag("provider_turn_details_7").performClick()
        compose.onNodeWithTag("provider_turn_completion").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(7, openedId) }
        compose.onNodeWithText("Full reason").assertExists()
        compose.onNodeWithTag("provider_turn_conversation").assertExists()

    }

    @Test fun completion_action_uses_selected_real_order_from_separate_detail_route() {
        val order = WorkOrder(7, "Ana Pérez", "Full reason", 1, WorkOrderStatus.Scheduled)
        var openedId: Int? = null
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), {}, {},
                initialSelectedId = 7, onCompletion = { openedId = it })
        } }
        compose.onNodeWithTag("provider_turn_completion").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(7, openedId) }
        compose.onNodeWithText("Full reason").assertExists()
        compose.onNodeWithTag("provider_turn_conversation").assertExists()
    }

    @Test fun enlarged_detail_keeps_full_reason_and_conversation_reachable() {
        val reason = "Repair the kitchen tap and replace the worn valve while preserving the original fittings"
        val order = WorkOrder(7, "Ana Pérez", reason, 1, WorkOrderStatus.Scheduled)
        var conversations = 0
        compose.setContent {
            val enlarged = Configuration(LocalConfiguration.current).apply { fontScale = 1.5f }
            androidx.compose.runtime.CompositionLocalProvider(LocalConfiguration provides enlarged) {
                LoresuelvoTheme { ProviderTurnDetailScreen(order, {}, { conversations++ }) }
            }
        }
        compose.onNodeWithText(reason).assertExists()
        compose.onNodeWithTag("provider_turn_conversation").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, conversations) }
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

    @Test fun home_selected_detail_back_returns_to_home_without_showing_list() {
        val order = WorkOrder(7, "Ana Pérez", "Complete reason", 1, WorkOrderStatus.Scheduled)
        var homeBackCalls = 0
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), { homeBackCalls++ }, {},
                initialSelectedId = order.id)
        } }
        compose.onNodeWithText("Detalle del turno").assertExists()
        compose.onNodeWithText("Volver").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, homeBackCalls) }
        compose.onNodeWithTag("provider_turns_list").assertDoesNotExist()
    }

    @Test fun conversation_action_uses_only_resolved_conversation_id() {
        val order = WorkOrder(40, "Ana", "Work", 1, WorkOrderStatus.Scheduled,
            serviceProposalId = 12, consumerId = 7)
        var opened = 0
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order), mapOf(40 to 93)), {}, {},
                onConversation = { opened = it })
        } }
        compose.onNodeWithTag("provider_turn_details_40").performClick()
        compose.onNodeWithTag("provider_turn_conversation").performScrollTo().performClick()
        org.junit.Assert.assertEquals(93, opened)
    }

    @Test fun conversation_action_without_resolved_link_stays_on_turns() {
        val order = WorkOrder(40, "Ana", "Work", 1, WorkOrderStatus.Scheduled,
            serviceProposalId = 12, consumerId = 7)
        var opened = 0
        var retries = 0
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), {}, { retries++ },
                onConversation = { opened = it })
        } }
        compose.onNodeWithTag("provider_turn_details_40").performClick()
        compose.onNodeWithTag("provider_turn_conversation").performScrollTo().performClick()
        org.junit.Assert.assertEquals(0, opened)
        compose.onNodeWithText("Detalle del turno").assertExists()
        compose.onNodeWithText("No encontramos la conversación de este turno.").assertExists()
        compose.onNodeWithText("Reintentar").assertExists()
        compose.onNodeWithText("Reintentar").performScrollTo().performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(1, retries)
            org.junit.Assert.assertEquals(0, opened)
        }
    }

    @Test fun proposal_lookup_error_shows_retry_notice_and_clears_after_resolution() {
        val order = WorkOrder(40, "Ana", "Work", 1, WorkOrderStatus.Scheduled,
            serviceProposalId = 12, consumerId = 7)
        var retries = 0
        var opened = 0
        var state: ProviderTurnsUiState.Ready by androidx.compose.runtime.mutableStateOf(
            ProviderTurnsUiState.Ready(listOf(order), proposalFailure = ServiceProposalListOutcome.Failure.Unavailable))
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(state, {}, {}, onConversation = { opened++ },
                onRetryConversation = { retries++; state = state.copy(conversationIds = mapOf(40 to 93), proposalFailure = null) })
        } }
        compose.onNodeWithTag("provider_turn_details_40").performClick()
        compose.onNodeWithTag("provider_turn_conversation").performScrollTo().performClick()
        compose.onNodeWithText("No pudimos cargar las propuestas.").assertExists()
        compose.onNodeWithText("Reintentar").performScrollTo().performClick()
        compose.onNodeWithText("No pudimos cargar las propuestas.").assertDoesNotExist()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(1, retries)
            org.junit.Assert.assertEquals(0, opened)
        }
    }

    @Test fun returning_from_chat_updates_selected_order_and_keeps_scrolled_list() {
        val orders = (1..20).map { WorkOrder(it, "Consumer $it", "Work", it.toLong(), WorkOrderStatus.Scheduled) }
        var state: ProviderTurnsUiState.Ready by androidx.compose.runtime.mutableStateOf(
            ProviderTurnsUiState.Ready(orders, mapOf(20 to 93)))
        var opened = 0
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(state, {}, {}, onConversation = { opened = it })
        } }
        compose.onNodeWithTag("provider_turns_list").performScrollToIndex(19)
        compose.onNodeWithTag("provider_turn_details_20").performClick()
        compose.onNodeWithTag("provider_turn_conversation").performScrollTo().performClick()
        compose.runOnIdle {
            org.junit.Assert.assertEquals(93, opened)
            state = state.copy(orders = orders.map { if (it.id == 20) it.copy(status = WorkOrderStatus.AwaitingPayment) else it })
        }
        compose.onNodeWithText("Pendiente de pago").assertExists()
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithTag("provider_turn_20").assertExists()
        compose.onNodeWithTag("provider_turn_1").assertDoesNotExist()
    }

    @Test fun summary_back_restores_list_scroll_without_firing_list_back() {
        val orders = (1..20).map { WorkOrder(it, "Consumer $it", "Work", it.toLong(), WorkOrderStatus.Scheduled) }
        var listBackCalls = 0
        compose.setContent { LoresuelvoTheme {
            ProviderTurnsScreen(ProviderTurnsUiState.Ready(orders), { listBackCalls++ }, {})
        } }
        compose.onNodeWithTag("provider_turns_list").performScrollToIndex(19)
        compose.onNodeWithTag("provider_turn_details_20").performClick()
        compose.onNodeWithText("Volver").performClick()
        compose.onNodeWithTag("provider_turn_20").assertExists()
        compose.onNodeWithTag("provider_turn_1").assertDoesNotExist()
        compose.runOnIdle { org.junit.Assert.assertEquals(0, listBackCalls) }
        compose.onNodeWithText("Volver").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, listBackCalls) }
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
