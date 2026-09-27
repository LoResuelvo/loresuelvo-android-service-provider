package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidencePreparer
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionViewModel
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR")
class ProviderCompletionRouteTest {
    @get:Rule val compose = createComposeRule()
    private val order = WorkOrder(42, "Ana Pérez", "Reparar la canilla", 1_000,
        WorkOrderStatus.Scheduled, serviceProposalId = 10, consumerId = 3)
    private var result: WorkOrderDetailOutcome = WorkOrderDetailOutcome.Success(
        WorkOrderDetail(42, 10, 3, 7, 100, 1_000, order.description, WorkOrderStatus.Scheduled, null))
    private val orders = object : WorkOrderRepository {
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = error("No list query")
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            assertEquals(42, id)
            return result
        }
    }
    private val accounts = object : CurrentAccountRepository {
        override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(
            CurrentAccount.Provider(7, "Juan", "Gómez", "juan@example.com", Category(1, "Plumbing"), null))
    }
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "provider@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun fresh_owned_order_shows_form_and_returns_to_summary() {
        var backs = 0
        showRoute { backs++ }
        compose.onNodeWithText("Ana Pérez").assertExists()
        compose.onNodeWithText("Reparar la canilla").assertExists()
        compose.onNodeWithText("Descripción de la entrega").assertExists()
        compose.onNodeWithTag("completion_submit").assertIsNotEnabled()
        compose.onNodeWithText("Volver").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun fresh_forbidden_response_never_shows_form() {
        result = WorkOrderDetailOutcome.Failure.Forbidden
        showRoute {}
        compose.onNodeWithText("No tenés permiso para informar la finalización de esta orden.").assertExists()
        compose.onNodeWithTag("completion_submit").assertDoesNotExist()
    }

    @Test fun missing_order_shows_safe_message_without_form() {
        result = WorkOrderDetailOutcome.Failure.NotFound
        showRoute {}
        compose.onNodeWithText("No encontramos esta orden.").assertExists()
        compose.onNodeWithTag("completion_submit").assertDoesNotExist()
    }

    @Test fun query_failure_retries_the_same_order() {
        result = WorkOrderDetailOutcome.Failure.Network(IOException("offline"))
        showRoute {}
        compose.onNodeWithText("No pudimos consultar esta orden.").assertExists()
        result = WorkOrderDetailOutcome.Success(
            WorkOrderDetail(42, 10, 3, 7, 100, 1_000, order.description, WorkOrderStatus.Scheduled, null))
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Descripción de la entrega").assertExists()
    }

    @Test fun route_draft_is_removed_when_session_ends() {
        val viewModel = showRoute {}
        compose.onNodeWithTag("completion_description").performTextInput("Private delivery note")
        compose.runOnIdle { assertEquals("Private delivery note", viewModel.description.value) }

        compose.runOnIdle { session.clearSession() }
        compose.onNodeWithTag("completion_description").assertDoesNotExist()
        compose.runOnIdle { assertEquals("", viewModel.description.value) }
    }

    private fun showRoute(onBack: () -> Unit): ProviderCompletionViewModel {
        val viewModel = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { 1_000 }, session,
            object : CompletionEvidencePreparer {
                override suspend fun prepare(source: String): EvidenceImagePreparation = error("No photo selected")
                override suspend fun clean(image: PreparedEvidenceImage) = Unit
            })
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionRoute(42, ProviderTurnsUiState.Ready(listOf(order)), onBack, {}, viewModel)
        } }
        return viewModel
    }
}
