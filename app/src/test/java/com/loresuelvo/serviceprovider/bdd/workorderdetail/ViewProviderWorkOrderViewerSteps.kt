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
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailViewModel
import io.cucumber.java.After
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
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
class ViewProviderWorkOrderViewerSteps {
    private val dispatcher = StandardTestDispatcher()
    private val session = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider", "p@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val report = WorkOrderCompletionReport(17, "Done", 1000,
        listOf("first", "second", "third").map { WorkOrderCompletionImage(it, "$it.jpg", "https://storage.test/$it") })
    private var detail = WorkOrderDetail(42, 10, 3, 7, 123456, 1000, "Original work",
        WorkOrderStatus.Paid, 17, report)
    private var detailCalls = 0
    private val orders = object : WorkOrderRepository {
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            detailCalls++
            return WorkOrderDetailOutcome.Success(detail)
        }
        override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(listOf(
            WorkOrder(42, "Ana Pérez", "Original work", 1000, WorkOrderStatus.Paid,
                serviceProposalId = 10, consumerId = 3)))
    }
    private val proposals = object : ServiceProposalRepository {
        override suspend fun list() = ServiceProposalListOutcome.Success(emptyList())
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome = error("Not used")
    }
    private val saved = SavedStateHandle(mapOf("turnId" to 42))
    private lateinit var model: ProviderTurnDetailViewModel
    private var origin = ""

    @Given("que abrí una orden con tres fotografías desde {string} después de desplazar su contenido")
    fun openedFrom(source: String) {
        Dispatchers.setMain(dispatcher)
        require(source == "Turnos" || source == "Inicio" || source == "chat")
        origin = source
        model = newModel()
        dispatcher.scheduler.advanceUntilIdle()
    }

    @And("estoy en {string}")
    fun atView(view: String) {
        if (view == "el visor de la segunda fotografía") model.selectFile("second")
    }

    @When("""^"(selecciono la segunda foto|pulso Atrás|elijo Cerrar|se recrea la pantalla|elijo Volver)"$""")
    fun act(selectedAction: String) {
        when (selectedAction) {
            "selecciono la segunda foto" -> model.selectFile("second")
            "pulso Atrás", "elijo Cerrar" -> if (model.selectedFileId.value != null) model.closeViewer()
            "se recrea la pantalla" -> {
                model = newModel()
                dispatcher.scheduler.advanceUntilIdle()
            }
            "elijo Volver" -> Unit
            else -> error("Unexpected viewer action")
        }
    }

    @Then("""^veo "(esa fotografía.*|el visor.*|el detalle.*|el listado.*|la conversación.*)"$""")
    fun seesResult(result: String) {
        val current = ((model.uiState.value as ProviderTurnDetailUiState.Ready).result.detail
            as WorkOrderDetailOutcome.Success).order
        when {
            result.startsWith("esa fotografía a tamaño completo") -> {
                assertEquals("second", model.selectedFileId.value)
                assertEquals("second.jpg", current.completionReport?.images?.get(1)?.originalName)
            }
            result.startsWith("el visor de esa misma fotografía") -> assertEquals("second", model.selectedFileId.value)
            result.startsWith("el detalle de la misma orden") -> {
                assertNull(model.selectedFileId.value)
                assertEquals(42, current.id)
            }
            result.startsWith("el listado de Turnos") -> {
                assertEquals("Turnos", origin)
                assertNull(model.selectedFileId.value)
            }
            result.startsWith("la conversación de origen") -> {
                assertEquals("chat", origin)
                assertNull(model.selectedFileId.value)
            }
            else -> error("Unexpected viewer result")
        }
    }

    @And("se conserva el contexto de regreso con el origen, la pestaña y la posición del listado o chat")
    fun keepsReturnContext() {
        assertTrue(origin.isNotBlank())
        assertEquals("provider_turns/42", Route.ProviderTurnDetail.buildPath(detail.id))
        assertEquals(42, ((model.uiState.value as ProviderTurnDetailUiState.Ready).result.detail
            as WorkOrderDetailOutcome.Success).order.id)
    }

    @Given("que veo una orden con reporte y tres fotografías")
    fun seesThreePhotos() {
        Dispatchers.setMain(dispatcher)
        model = newModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, detail.completionReport?.images?.size)
    }

    @And("una fotografía falla al cargar mientras las otras siguen disponibles")
    fun onePhotoFails() {
        model.selectFile("second")
        assertEquals("second", model.selectedFileId.value)
        assertEquals(listOf("first", "second", "third"), detail.completionReport?.images?.map { it.fileId })
    }

    @And("una nueva consulta de la misma orden devuelve direcciones temporales vigentes")
    fun renewedUrlsExist() {
        detail = detail.copy(completionReport = report.copy(images = report.images.map {
            it.copy(url = "https://storage.test/new-${it.fileId}")
        }))
    }

    @When("elijo Reintentar la fotografía fallida")
    fun retriesFailedPhoto() {
        model.retryPhoto("second")
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Then("se consulta nuevamente el detalle y puedo ver la fotografía con su dirección vigente")
    fun seesRenewedPhoto() {
        assertEquals(2, detailCalls)
        val current = ((model.uiState.value as ProviderTurnDetailUiState.Ready).result.detail
            as WorkOrderDetailOutcome.Success).order
        assertEquals("https://storage.test/new-second", current.completionReport?.images?.get(1)?.url)
    }

    @And("se conservan el reporte, el orden de las fotos y la selección del visor si estaba abierto")
    fun keepsReportAndViewer() {
        val current = ((model.uiState.value as ProviderTurnDetailUiState.Ready).result.detail
            as WorkOrderDetailOutcome.Success).order
        assertEquals(17, current.completionReportId)
        assertEquals(listOf("first", "second", "third"), current.completionReport?.images?.map { it.fileId })
        assertEquals("second", model.selectedFileId.value)
    }

    @And("las demás fotografías y los datos del servicio siguen utilizables")
    fun otherDataRemains() {
        val current = ((model.uiState.value as ProviderTurnDetailUiState.Ready).result.detail
            as WorkOrderDetailOutcome.Success).order
        assertEquals("Original work", current.description)
        assertEquals(3, current.completionReport?.images?.size)
        assertEquals("https://storage.test/new-first", current.completionReport?.images?.first()?.url)
        assertEquals("https://storage.test/new-third", current.completionReport?.images?.last()?.url)
        assertEquals(WorkOrderStatus.Paid, current.status)
    }

    private fun newModel() = ProviderTurnDetailViewModel(saved,
        GetProviderWorkOrderDetailUseCase(orders), GetServiceProposalsUseCase(proposals), session)

    @After fun tearDown() {
        if (!::model.isInitialized) return
        Dispatchers.setMain(dispatcher)
        session.clearSession()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }
}
