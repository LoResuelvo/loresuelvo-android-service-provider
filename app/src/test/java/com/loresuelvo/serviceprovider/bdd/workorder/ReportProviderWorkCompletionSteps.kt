package com.loresuelvo.serviceprovider.bdd.workorder

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.CompletionEligibility
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderTurnsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderCompletionViewModel
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsViewModel
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.cucumber.java.en.Then
import io.cucumber.java.en.And
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ReportProviderWorkCompletionSteps {
    private val dispatcher = StandardTestDispatcher()
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "provider@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val selected = WorkOrder(42, "Ana Pérez", "Reparar la canilla y verificar la instalación",
        1_000, WorkOrderStatus.Scheduled, serviceProposalId = 10, consumerId = 3)
    private var detail = WorkOrderDetail(42, 10, 3, 7, 100, 1_000, selected.description,
        WorkOrderStatus.Scheduled, null)
    private var now = 1_000L
    private var detailCalls = 0
    private val orders = object : WorkOrderRepository {
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = ActivityLoadOutcome.Success(listOf(selected))
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            assertEquals(selected.id, id)
            detailCalls++
            return WorkOrderDetailOutcome.Success(detail)
        }
    }
    private val accounts = object : CurrentAccountRepository {
        override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(
            CurrentAccount.Provider(7, "Juan", "Gómez", "juan@example.com", Category(1, "Plomería"), null))
    }
    private val proposals = object : ServiceProposalRepository {
        override suspend fun list() = ServiceProposalListOutcome.Success(listOf(ServiceProposalSummary(
            10, 93, 100, 1_000, selected.description, 60, ServiceProposalStatus.Accepted, 1,
            ServiceProposalCounterpart(3, "consumer", "Ana", "Pérez", null, null),
            ServiceProposalBookingTerms("ARS", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        )))
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
            error("Completion must not create proposals")
    }
    private lateinit var turns: ProviderTurnsViewModel
    private lateinit var completion: ProviderCompletionViewModel
    private lateinit var initialDetail: WorkOrderDetail

    @Given("que inicié sesión como prestador")
    fun signedIn() { Dispatchers.setMain(dispatcher) }

    @Given("que consulto el resumen de un turno real de Ana Pérez desde Turnos")
    fun selectedTurnSummary() = runTest(dispatcher.scheduler) {
        turns = ProviderTurnsViewModel(GetProviderTurnsUseCase(orders), GetServiceProposalsUseCase(proposals), session)
        advanceUntilIdle()
        assertEquals(selected, (turns.uiState.value as ProviderTurnsUiState.Ready).orders.single())
    }

    @And("la consulta vigente de esa orden indica {string}")
    fun currentDetailSituation(situation: String) {
        detail = when (situation) {
            "soy el asignado, está scheduled y aún no llegó el turno" -> { now = 999; detail }
            "soy el asignado, está scheduled y llegó la hora del turno" -> detail
            "soy el asignado, está scheduled y el turno ya pasó" -> { now = 1_001; detail }
            "la orden ya está awaiting_payment con reporte" ->
                detail.copy(status = WorkOrderStatus.AwaitingPayment, completionReportId = 17)
            "la orden ya está paid con reporte" ->
                detail.copy(status = WorkOrderStatus.Paid, completionReportId = 17)
            "no soy el prestador asignado" -> detail.copy(providerId = 99)
            else -> error("Unapproved situation: $situation")
        }
        initialDetail = detail
    }

    @When("intento abrir Informar finalización")
    fun openCompletion() = runTest(dispatcher.scheduler) {
        completion = ProviderCompletionViewModel(GetCompletionEligibilityUseCase(orders, accounts) { now }, session)
        completion.open((turns.uiState.value as ProviderTurnsUiState.Ready).orders.single())
        advanceUntilIdle()
    }

    @Then("veo {string} para esa misma orden")
    fun seeCurrentResult(result: String) {
        val state = completion.uiState.value as ProviderCompletionUiState.Ready
        assertEquals(selected.id, state.order.id)
        assertEquals(when (result) {
            "una explicación de que todavía no se puede" -> CompletionEligibility.TooEarly
            "el formulario con consumidor y trabajo" -> CompletionEligibility.Eligible
            "el estado vigente sin ofrecer otro reporte" -> CompletionEligibility.AlreadyReported
            "un aviso de falta de permisos" -> CompletionEligibility.Forbidden
            else -> error("Unapproved result: $result")
        }, state.eligibility)
        if (state.eligibility == CompletionEligibility.Eligible) {
            assertEquals("Ana Pérez", state.order.consumerName)
            assertEquals(initialDetail.description, state.order.description)
        }
    }

    @And("conservo el acceso al motivo completo y a su conversación desde el resumen")
    fun summaryKeepsReasonAndConversation() {
        val state = turns.uiState.value as ProviderTurnsUiState.Ready
        assertEquals(selected.description, state.orders.single().description)
        assertEquals(93, state.conversationIds[selected.id])
    }

    @And("no se registra ninguna finalización")
    fun noCompletionRecorded() {
        assertEquals(1, detailCalls)
        assertEquals(initialDetail, detail)
    }

    @After fun tearDown() { Dispatchers.resetMain() }
}
