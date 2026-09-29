package com.loresuelvo.serviceprovider.bdd.workorderdetail

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderCompletionImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderCompletionReport
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderWorkOrderDetailUseCase
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailViewModel
import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ViewProviderWorkOrderRecoverySteps {
    private val dispatcher = StandardTestDispatcher()
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "p@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val proposals = GetServiceProposalsUseCase(object : ServiceProposalRepository {
        override suspend fun list() = ServiceProposalListOutcome.Success(emptyList())
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome = error("Not used")
    })
    private val previous = WorkOrderDetail(7, 10, 3, 7, 1000, 1000, "Old private order",
        WorkOrderStatus.Paid, 17, WorkOrderCompletionReport(17, "Old report", 1000,
            listOf(WorkOrderCompletionImage("old-file", "old.jpg", "https://storage.test/old"))))
    private val pending = CompletableDeferred<WorkOrderDetailOutcome>()
    private lateinit var previousModel: ProviderTurnDetailViewModel
    private lateinit var model: ProviderTurnDetailViewModel
    private var response = ""

    @Given("que estaba viendo otra orden y la consulta de la orden elegida obtiene {string}")
    fun previousOrderAndResponse(value: String) {
        Dispatchers.setMain(dispatcher)
        response = value
        previousModel = newModel(7) { WorkOrderDetailOutcome.Success(previous) }
        dispatcher.scheduler.advanceUntilIdle()
        previousModel.selectFile("old-file")
        assertEquals("old-file", previousModel.selectedFileId.value)
    }

    @When("abro el detalle de la orden elegida")
    fun openChosenOrder() {
        model = newModel(42) { pending.await() }
        dispatcher.scheduler.runCurrent()
    }

    @Then("mientras espero veo carga sin los datos de la orden anterior")
    fun loadingIsPrivate() {
        assertEquals(ProviderTurnDetailUiState.Loading, model.uiState.value)
        assertNull(model.selectedFileId.value)
        assertEquals(42, model.orderId)
    }

    @And("después veo {string}")
    fun seesRecovery(result: String) {
        val failure = when (response) {
            "error de red" -> WorkOrderDetailOutcome.Failure.Network(Exception("offline"))
            "error del servidor" -> WorkOrderDetailOutcome.Failure.Server(503)
            "acceso denegado 403" -> WorkOrderDetailOutcome.Failure.Forbidden
            "orden inexistente 404" -> WorkOrderDetailOutcome.Failure.NotFound
            "sesión inválida 401" -> WorkOrderDetailOutcome.Failure.Unauthorized
            else -> error("Unexpected response")
        }
        pending.complete(failure)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(failure, (model.uiState.value as ProviderTurnDetailUiState.Error).failure)
        when (failure) {
            WorkOrderDetailOutcome.Failure.Unauthorized -> {
                assertTrue(result.contains("autenticación"))
                assertNull(session.getSession())
                assertNull(previousModel.selectedFileId.value)
            }
            WorkOrderDetailOutcome.Failure.Forbidden, WorkOrderDetailOutcome.Failure.NotFound ->
                assertTrue(result.contains("salida accesible"))
            else -> assertTrue(result.contains("Reintentar"))
        }
    }

    @And("no se muestran datos privados de una sesión anterior ni se da una orden por pagada por error")
    fun noStalePrivateData() {
        assertTrue(model.uiState.value is ProviderTurnDetailUiState.Error)
        assertNull(model.selectedFileId.value)
    }

    private fun newModel(id: Int, detail: suspend () -> WorkOrderDetailOutcome): ProviderTurnDetailViewModel {
        val orders = object : WorkOrderRepository {
            override suspend fun getWorkOrder(id: Int) = detail()
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(emptyList<WorkOrder>())
        }
        return ProviderTurnDetailViewModel(SavedStateHandle(mapOf("turnId" to id)),
            GetProviderWorkOrderDetailUseCase(orders), proposals, session)
    }

    @After fun tearDown() {
        if (!::model.isInitialized) return
        Dispatchers.setMain(dispatcher)
        session.clearSession()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }
}
