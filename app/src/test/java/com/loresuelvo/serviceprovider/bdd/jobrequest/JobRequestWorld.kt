package com.loresuelvo.serviceprovider.bdd.jobrequest

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestImage
import com.loresuelvo.serviceprovider.domain.usecase.activity.AcceptJobRequestUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetScheduledWorkUseCase
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeViewModel
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailEffect
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailUiState
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailViewModel
import com.loresuelvo.serviceprovider.ui.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class JobRequestWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(dispatcher)
    private val repository = JobRequestRepositoryFake()
    private val workOrderRepository = EmptyWorkOrderRepository()
    private var effectsJob: Job? = null
    private var lastEffect: JobRequestDetailEffect? = null
    private var selectedImageIndex: Int? = null
    private var conversationPath: String? = null
    private var temporaryConversationVisible = false
    private var detailOnBackStack = true
    private lateinit var viewModel: JobRequestDetailViewModel

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun givenPendingRequest() = arrangeRequest()

    fun givenRequestWithImages() = arrangeRequest(
        images = listOf(JobRequestImage("image-1", "https://cdn.example/image-1", "pileta.jpg")),
    )

    fun givenRequestWithoutImages() = arrangeRequest()

    fun givenTemporaryAcceptanceFailure() {
        arrangeRequest()
        repository.acceptOutcomes += AcceptJobRequestOutcome.Failure.Network(IllegalStateException("offline"))
        repository.acceptOutcomes += AcceptJobRequestOutcome.Success(REQUEST_ID, CONVERSATION_ID)
        openDetail()
        selectContinue()
    }

    fun givenStaleAcceptance() {
        arrangeRequest()
        repository.acceptOutcomes += AcceptJobRequestOutcome.Failure.Conflict
    }

    fun givenAcceptedConversation() {
        arrangeRequest()
        repository.acceptOutcomes += AcceptJobRequestOutcome.Success(REQUEST_ID, CONVERSATION_ID)
        openDetail()
        selectContinue()
    }

    fun givenOpenedPendingRequest() {
        arrangeRequest()
        openDetail()
    }

    fun openDetail() {
        viewModel = JobRequestDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Route.JobRequestDetail.argument to REQUEST_ID)),
            getPendingJobRequest = GetPendingJobRequestUseCase(repository),
            acceptJobRequest = AcceptJobRequestUseCase(repository),
        )
        scheduler.advanceUntilIdle()
    }

    fun selectThumbnail() {
        ensureDetailLoaded()
        assertTrue(currentRequest().images.isNotEmpty())
        selectedImageIndex = 0
    }

    fun closeImageViewerAndAssertDetail() {
        selectedImageIndex = null
        assertEquals(JobRequestDetailUiState.Ready(request()), viewModel.uiState.value)
        assertEquals(0, repository.acceptCalls)
    }

    fun selectContinue() {
        ensureDetailLoaded()
        observeEffects()
        viewModel.accept()
        scheduler.advanceUntilIdle()
    }

    fun retryAcceptance() {
        viewModel.retryAccept()
        scheduler.advanceUntilIdle()
    }

    fun processAcceptanceConfirmation() {
        val effect = lastEffect as JobRequestDetailEffect.Accepted
        conversationPath = Route.Conversation.buildPath(effect.conversationId)
        temporaryConversationVisible = true
        detailOnBackStack = false
    }

    fun closeDetail() {
        ensureDetailLoaded()
        detailOnBackStack = false
    }

    fun recreateDetail() = openDetail()

    fun assertRequestDetails() {
        assertEquals(JobRequestDetailUiState.Ready(request()), viewModel.uiState.value)
    }

    fun assertAcceptAvailable() = assertTrue(viewModel.uiState.value is JobRequestDetailUiState.Ready)

    fun assertImageViewer() {
        assertEquals(0, selectedImageIndex)
        assertEquals("pileta.jpg", currentRequest().images.single().originalName)
    }

    fun assertNoImageGallery() {
        assertTrue(currentRequest().images.isEmpty())
        assertNull(selectedImageIndex)
    }

    fun assertAcceptanceProgress() {
        assertTrue(viewModel.uiState.value is JobRequestDetailUiState.Accepting)
        assertEquals(1, repository.acceptCalls)
    }

    fun assertAcceptanceConfirmed() {
        assertEquals(JobRequestDetailEffect.Accepted(REQUEST_ID, CONVERSATION_ID), lastEffect)
    }

    fun assertAcceptedRequestRemovedFromHome() {
        val homeViewModel = ProviderHomeViewModel(
            getPendingJobRequests = GetPendingJobRequestsUseCase(repository),
            getScheduledWork = GetScheduledWorkUseCase(workOrderRepository),
        )
        scheduler.advanceUntilIdle()
        homeViewModel.removeJobRequest(REQUEST_ID)
        val requests = homeViewModel.uiState.value.jobRequests as ActivitySectionState.Ready
        assertTrue(requests.items.isEmpty())
    }

    fun assertAcceptanceRetriedOnce() {
        assertEquals(2, repository.acceptCalls)
        assertEquals(JobRequestDetailEffect.Accepted(REQUEST_ID, CONVERSATION_ID), lastEffect)
    }

    fun assertRequestDataVisible() = assertEquals(request(), currentRequest())

    fun assertRequestUnavailable() = assertTrue(
        viewModel.uiState.value is JobRequestDetailUiState.AcceptUnavailable,
    )

    fun assertNoAcceptanceSuccess() = assertNull(lastEffect)

    fun assertConversationPath() = assertEquals(
        Route.Conversation.buildPath(CONVERSATION_ID),
        conversationPath,
    )

    fun assertTemporaryConversationDestination() = assertTrue(temporaryConversationVisible)

    fun assertDetailRemovedFromBackStack() = assertFalse(detailOnBackStack)

    fun assertHomeWithoutAcceptance() = assertEquals(0, repository.acceptCalls)

    fun assertRequestRemainsPending() {
        assertTrue(detailOnBackStack.not())
        assertEquals(request(), repository.pending.items.single())
    }

    fun assertRecreatedDetail() = assertEquals(JobRequestDetailUiState.Ready(request()), viewModel.uiState.value)

    fun assertNoAcceptanceReplay() = assertEquals(0, repository.acceptCalls)

    private fun arrangeRequest(images: List<JobRequestImage> = emptyList()) {
        repository.reset()
        repository.pending = ActivityLoadOutcome.Success(listOf(request(images)))
    }

    private fun ensureDetailLoaded() {
        if (!::viewModel.isInitialized) openDetail()
    }

    private fun observeEffects() {
        lastEffect = null
        effectsJob?.cancel()
        effectsJob = scope.launch {
            viewModel.effects.collect { lastEffect = it }
        }
        scheduler.runCurrent()
    }

    private fun currentRequest(): JobRequest = when (val state = viewModel.uiState.value) {
        is JobRequestDetailUiState.Ready -> state.request
        is JobRequestDetailUiState.Accepting -> state.request
        is JobRequestDetailUiState.AcceptError -> state.request
        is JobRequestDetailUiState.AcceptUnavailable -> state.request
        else -> error("Request detail is not available")
    }

    private fun request(images: List<JobRequestImage> = repository.pending.items.first().images) =
        JobRequest(REQUEST_ID, "Ana Pérez", "Reparar pérdida", "Pérdida debajo de la pileta", images)

    private fun request() = request(repository.pending.items.first().images)

    override fun close() {
        effectsJob?.cancel()
        scope.cancel()
        Dispatchers.resetMain()
    }

    private companion object {
        const val REQUEST_ID = 7
        const val CONVERSATION_ID = 11
    }
}
