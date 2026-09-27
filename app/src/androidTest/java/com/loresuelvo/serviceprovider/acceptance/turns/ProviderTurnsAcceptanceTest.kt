package com.loresuelvo.serviceprovider.acceptance.turns

import androidx.activity.ComponentActivity
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeUiState
import com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNavHost
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.home.ProviderHomeScreen
import com.loresuelvo.serviceprovider.ui.screens.turns.ProviderTurnsScreen
import com.loresuelvo.serviceprovider.ui.screens.turns.ProviderTurnsRoute
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderTurnsAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val order = WorkOrder(40, "Ana Pérez", "Repair the kitchen tap and replace the worn valve",
        1, WorkOrderStatus.Scheduled, amountCents = 1500050, consumerGivenName = "Ana",
        consumerSurname = "Pérez", serviceProposalId = 12, consumerId = 7)

    @Test fun home_card_opens_loaded_detail_and_back_returns_home() {
        compose.setContent {
            val nav = rememberNavController()
            LoResuelvoNavHost(nav, Route.Home.path,
                welcome = {}, professionalProfile = {}, optionalIdentityVerification = {},
                home = {
                    ProviderHomeScreen(
                        provider = CurrentAccount.Provider(1, "Carlos", "Gómez", "provider@example.com",
                            Category(1, "Plomería"), null),
                        uiState = ProviderHomeUiState(ActivitySectionState.Ready(emptyList()),
                            ActivitySectionState.Ready(listOf(order))),
                        onRetryJobRequests = {}, onRetryScheduledWork = {}, onJobRequestClick = {},
                        onMercadoPagoClick = {},
                        onAllTurnsClick = { nav.navigate(Route.ProviderTurns.path) },
                        onTurnDetailsClick = { nav.navigate(Route.ProviderTurnDetail.buildPath(it.id)) },
                    )
                },
                providerTurns = {
                    ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), { nav.popBackStack() }, {})
                },
                providerTurnDetail = { id ->
                    ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), { nav.popBackStack() }, {},
                        initialSelectedId = id)
                },
                messages = {}, profile = {}, jobRequestDetail = {}, conversation = {},
            )
        }
        compose.onNodeWithTag("provider_turn_details_40").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.provider_turns_detail_title)).assertIsDisplayed()
        compose.onNodeWithText(order.description).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.provider_turns_back)).performClick()
        compose.onNodeWithTag("home_turns_row").assertIsDisplayed()
        compose.onNodeWithTag("scheduled_view_all_turns").performScrollTo().performClick()
        compose.onNodeWithTag("provider_turns_list").assertIsDisplayed()
    }

    @Test fun retry_missing_contact_never_opens_an_unrelated_chat() {
        var opened = 0
        var retries = 0
        var state by mutableStateOf(ProviderTurnsUiState.Ready(listOf(order)))
        compose.setContent {
            ProviderTurnsScreen(state, {}, {}, onConversation = { opened = it },
                onRetryConversation = {
                    retries++
                    state = state.copy(conversationIds = mapOf(order.id to 93))
                })
        }
        compose.onNodeWithTag("provider_turn_details_40").performClick()
        compose.onNodeWithTag("provider_turn_conversation").performScrollTo().performClick()
        assertEquals(0, opened)
        compose.onNodeWithText(compose.activity.getString(R.string.provider_turns_conversation_missing)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.provider_home_retry)).performScrollTo().performClick()
        assertEquals(1, retries)
        compose.onNodeWithTag("provider_turn_conversation").performClick()
        assertEquals(93, opened)
    }

    @Test fun chat_return_refreshes_status_and_preserves_list_position() {
        val orders = (1..20).map { WorkOrder(it, "Consumer $it", "Work", it.toLong(), WorkOrderStatus.Scheduled) }
        var state by mutableStateOf(ProviderTurnsUiState.Ready(orders, mapOf(20 to 93)))
        var opened = 0
        compose.setContent {
            ProviderTurnsScreen(state, {}, {}, onConversation = {
                opened = it
                state = state.copy(orders = orders.map { order ->
                    if (order.id == 20) order.copy(status = WorkOrderStatus.AwaitingPayment) else order
                })
            })
        }
        compose.onNodeWithTag("provider_turns_list").performScrollToIndex(19)
        compose.onNodeWithTag("provider_turn_details_20").performClick()
        compose.onNodeWithTag("provider_turn_conversation").performScrollTo().performClick()
        assertEquals(93, opened)
        compose.onNodeWithText(compose.activity.getString(R.string.provider_turns_status_awaiting_payment)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.provider_turns_back)).performClick()
        compose.onNodeWithTag("provider_turn_20").assertIsDisplayed()
        compose.onNodeWithTag("provider_turn_1").assertDoesNotExist()
    }

    @Test fun loading_empty_and_error_keep_accessible_retry() {
        var state: ProviderTurnsUiState by mutableStateOf(ProviderTurnsUiState.Loading)
        compose.setContent { ProviderTurnsScreen(state, {}, { state = ProviderTurnsUiState.Ready(listOf(order)) }) }
        compose.onNodeWithTag("provider_turns_loading").assertIsDisplayed()
        compose.runOnIdle { state = ProviderTurnsUiState.Ready(emptyList()) }
        compose.onNodeWithTag("provider_turns_empty").assertIsDisplayed()
        compose.runOnIdle { state = ProviderTurnsUiState.Error }
        compose.onNodeWithTag("provider_turns_error").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.provider_home_retry)).performClick()
        compose.onNodeWithTag("provider_turn_40").assertIsDisplayed()
    }

    @Test fun enlarged_text_keeps_card_and_detail_actions_touchable() {
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply { fontScale = 1.5f }
            CompositionLocalProvider(LocalConfiguration provides config) {
                ProviderTurnsScreen(ProviderTurnsUiState.Ready(listOf(order)), {}, {})
            }
        }
        compose.onNodeWithTag("provider_turn_details_40").assertIsDisplayed().performClick()
        compose.onNodeWithText(order.description).assertIsDisplayed()
        compose.onNodeWithTag("provider_turn_conversation").performScrollTo().assertIsDisplayed()
    }

    @Test fun unauthorized_reload_removes_old_orders_and_clears_session() {
        var unauthorized = false
        val session = object : AuthSessionStore {
            override val sessionFlow = MutableStateFlow<AuthSession?>(
                AuthSession(User("provider", "provider@example.com"), "test-token"))
            override fun getSession() = sessionFlow.value
            override fun saveSession(session: AuthSession) { sessionFlow.value = session }
            override fun clearSession() { sessionFlow.value = null }
        }
        val orders = object : WorkOrderRepository {
            override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> =
                if (unauthorized) ActivityLoadOutcome.Failure.Unauthorized
                else ActivityLoadOutcome.Success(listOf(order))
        }
        val proposals = object : ServiceProposalRepository {
            override suspend fun list() = ServiceProposalListOutcome.Success(emptyList())
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
                error("Creation is outside this test")
        }
        val viewModel = ProviderTurnsViewModel(GetProviderTurnsUseCase(orders),
            GetServiceProposalsUseCase(proposals), session)
        compose.setContent { ProviderTurnsRoute({}, viewModel = viewModel) }
        compose.onNodeWithTag("provider_turn_40").assertIsDisplayed()
        compose.runOnIdle { unauthorized = true; viewModel.load() }
        compose.waitForIdle()
        compose.onNodeWithTag("provider_turn_40").assertDoesNotExist()
        compose.onNodeWithTag("provider_turns_error").assertIsDisplayed()
        assertEquals(null, session.getSession())
    }
}
