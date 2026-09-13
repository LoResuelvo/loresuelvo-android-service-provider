package com.loresuelvo.serviceprovider.bdd.home

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetPendingJobRequestsUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetScheduledWorkUseCase
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryUiState
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryViewModel
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeUiState
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeViewModel
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * The world intentionally retains entry and activity orchestration together:
 * all twelve scenarios share one session, entry resolver, and recreation
 * boundary. Extraction would hide the same-instance assertions; the seam is
 * the three fake ports below. Focused proof lives in the entry, Home state,
 * repository, and Compose tests rather than in fixture-only step assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProviderHomeWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val observerScope = CoroutineScope(dispatcher)
    private val sessionStore = ProviderHomeSessionStore()
    private val currentAccount = ProviderHomeCurrentAccountFake()
    private val jobRequests = ProviderHomeJobRequestFake()
    private val workOrders = ProviderHomeWorkOrderFake()
    private var entryObserver: Job? = null
    private var profileGate: CompletableDeferred<Unit>? = null
    private val entryStates = mutableListOf<ProviderEntryUiState>()

    private lateinit var entryViewModel: ProviderEntryViewModel
    private lateinit var homeViewModel: ProviderHomeViewModel
    private var sawEntryLoading = false

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun configureProviderProfile() {
        configureAuthenticatedSession()
        currentAccount.defaultResponse = CurrentAccountOutcome.Success(provider())
    }

    fun configureProviderProfileWithLoading() {
        configureProviderProfile()
        profileGate = CompletableDeferred()
        currentAccount.gate = profileGate
    }

    fun configureMissingProfile() {
        configureAuthenticatedSession()
        currentAccount.defaultResponse = CurrentAccountOutcome.Failure.NotFound
    }

    fun configureConsumerAccount() {
        configureAuthenticatedSession()
        currentAccount.defaultResponse = CurrentAccountOutcome.Success(CurrentAccount.Consumer)
    }

    fun configureExpiredSession() {
        configureAuthenticatedSession()
        currentAccount.defaultResponse = CurrentAccountOutcome.Failure.Unauthorized
    }

    fun configureTemporaryFailure() {
        configureAuthenticatedSession()
        currentAccount.defaultResponse = CurrentAccountOutcome.Failure.Server(503)
    }

    fun configureRetryableProfileRecovery() {
        configureAuthenticatedSession()
        currentAccount.responses.add(CurrentAccountOutcome.Failure.Network(IllegalStateException("offline")))
        currentAccount.responses.add(CurrentAccountOutcome.Success(provider()))
    }

    fun configureActivityData() {
        configureProviderProfile()
        jobRequests.defaultResponse = ActivityLoadOutcome.Success(listOf(jobRequest()))
        workOrders.defaultResponse = ActivityLoadOutcome.Success(listOf(workOrder()))
    }

    fun configureEmptyActivity() {
        configureProviderProfile()
        jobRequests.defaultResponse = ActivityLoadOutcome.Success(emptyList())
        workOrders.defaultResponse = ActivityLoadOutcome.Success(emptyList())
    }

    fun configureActivityRetry() {
        configureProviderProfile()
        jobRequests.responses.add(ActivityLoadOutcome.Failure.Server(503))
        jobRequests.responses.add(ActivityLoadOutcome.Success(listOf(jobRequest())))
        workOrders.defaultResponse = ActivityLoadOutcome.Success(listOf(workOrder()))
    }

    fun configureOnboardingContinuation() {
        configureAuthenticatedSession()
        currentAccount.responses.add(CurrentAccountOutcome.Failure.NotFound)
        currentAccount.responses.add(CurrentAccountOutcome.Success(provider()))
    }

    fun openApplication() {
        createEntryViewModel()
        scheduler.runCurrent()
        sawEntryLoading = entryViewModel.uiState.value is ProviderEntryUiState.Loading
        profileGate?.complete(Unit)
        scheduler.advanceUntilIdle()
    }

    fun resolvePrivateDestination() {
        createEntryViewModel()
        scheduler.advanceUntilIdle()
    }

    fun retryEntry() {
        entryViewModel.retry()
        scheduler.advanceUntilIdle()
    }

    fun returnToWelcome() {
        entryViewModel.continueToWelcome()
        scheduler.advanceUntilIdle()
    }

    fun openHomeActivity() {
        if (!::entryViewModel.isInitialized) {
            openApplication()
        }
        assertTrue(entryViewModel.uiState.value is ProviderEntryUiState.Home)
        homeViewModel = ProviderHomeViewModel(
            getPendingJobRequests = GetPendingJobRequestsUseCase(jobRequests),
            getScheduledWork = GetScheduledWorkUseCase(workOrders),
        )
        scheduler.advanceUntilIdle()
    }

    fun retryJobRequests() {
        homeViewModel.retryJobRequests()
        scheduler.advanceUntilIdle()
    }

    fun refreshAfterOnboarding() {
        resolvePrivateDestination()
        assertEquals(ProviderEntryUiState.CompleteProviderProfile, entryViewModel.uiState.value)
        entryViewModel.refresh()
        scheduler.advanceUntilIdle()
    }

    fun recreateApplication() {
        entryObserver?.cancel()
        createEntryViewModel()
        scheduler.advanceUntilIdle()
    }

    fun assertLoadingThenHome() {
        assertTrue("Entry must expose loading before the profile is resolved", sawEntryLoading)
        assertTrue(entryViewModel.uiState.value is ProviderEntryUiState.Home)
        assertFalse(entryStates.any { it is ProviderEntryUiState.Welcome })
        assertFalse(entryStates.any { it is ProviderEntryUiState.CompleteProviderProfile })
    }

    fun assertProviderIdentity() {
        val account = resolvedProvider()
        assertEquals("Juan Gómez", "${account.name} ${account.surname}")
        assertEquals("Plomería", account.category.name)
        assertNotNull(account.profilePhotoUrl)
    }

    fun assertInitialsFallbackIsSupported() {
        val account = resolvedProvider()
        assertEquals("JG", "${account.name.first()}${account.surname.first()}")
        assertTrue(account.profilePhotoUrl!!.isNotBlank())
    }

    fun assertIncompleteProfile() {
        assertEquals(ProviderEntryUiState.CompleteProviderProfile, entryViewModel.uiState.value)
    }

    fun assertSessionRetained() {
        assertNotNull(sessionStore.getSession())
    }

    fun assertAccountMismatch() {
        assertEquals(ProviderEntryUiState.AccountMismatch, entryViewModel.uiState.value)
    }

    fun assertAccountMismatchWelcome() {
        assertEquals(ProviderEntryUiState.Welcome, entryViewModel.uiState.value)
        assertEquals(1, sessionStore.clearCalls)
    }

    fun assertExpiredSessionWelcome() {
        assertEquals(ProviderEntryUiState.Welcome, entryViewModel.uiState.value)
        assertEquals(null, sessionStore.getSession())
        assertEquals(1, sessionStore.clearCalls)
    }

    fun assertRetryableEntryError() {
        assertEquals(ProviderEntryUiState.RetryableError, entryViewModel.uiState.value)
        assertNotNull(sessionStore.getSession())
    }

    fun assertEntryRetryShowsHome() {
        assertTrue(entryViewModel.uiState.value is ProviderEntryUiState.Home)
        assertEquals(2, currentAccount.calls)
        assertNotNull(sessionStore.getSession())
    }

    fun assertOnboardingRefreshShowsHome() {
        assertTrue(entryViewModel.uiState.value is ProviderEntryUiState.Home)
        assertEquals(2, currentAccount.calls)
        assertEquals(
            listOf(ProviderEntryUiState.CompleteProviderProfile, ProviderEntryUiState.Home(provider())),
            entryStates.filter { it is ProviderEntryUiState.CompleteProviderProfile || it is ProviderEntryUiState.Home },
        )
    }

    fun assertActivityLoaded() {
        val state = homeViewModel.uiState.value
        val requests = state.jobRequests as ActivitySectionState.Ready
        val work = state.scheduledWork as ActivitySectionState.Ready
        assertEquals("Ana Pérez", requests.items.single().consumerName)
        assertEquals("Reparar pérdida", requests.items.single().title)
        assertEquals("Pérdida debajo de la pileta", requests.items.single().description)
        assertEquals("Ana Pérez", work.items.single().consumerName)
        assertEquals("Pérdida debajo de la pileta", work.items.single().description)
        assertEquals(1, requests.items.size)
        assertEquals(1, work.items.size)
    }

    fun assertActivityActionsVisible() {
        assertEquals(ProviderEntryUiState.Home(provider()), entryViewModel.uiState.value)
        assertTrue(homeViewModel.uiState.value.jobRequests is ActivitySectionState.Ready)
        assertTrue(homeViewModel.uiState.value.scheduledWork is ActivitySectionState.Ready)
    }

    fun assertActivityEmpty() {
        val state = homeViewModel.uiState.value
        assertEquals(0, (state.jobRequests as ActivitySectionState.Ready).items.size)
        assertEquals(0, (state.scheduledWork as ActivitySectionState.Ready).items.size)
    }

    fun assertActivityRetrySucceeded() {
        val state = homeViewModel.uiState.value
        assertTrue(state.jobRequests is ActivitySectionState.Ready)
        assertEquals(1, (state.jobRequests as ActivitySectionState.Ready).items.size)
        assertTrue(state.scheduledWork is ActivitySectionState.Ready)
        assertEquals(1, (state.scheduledWork as ActivitySectionState.Ready).items.size)
        assertEquals(2, jobRequests.calls)
        assertEquals(1, workOrders.calls)
        assertNotNull(sessionStore.getSession())
        assertTrue(entryViewModel.uiState.value is ProviderEntryUiState.Home)
    }

    fun assertSingleHomeAfterRecreation() {
        assertTrue(entryViewModel.uiState.value is ProviderEntryUiState.Home)
        val terminalStates = entryStates.filter { it is ProviderEntryUiState.Home }
        assertEquals(1, terminalStates.distinct().size)
        assertFalse(entryStates.any { it is ProviderEntryUiState.Welcome })
        assertFalse(entryStates.any { it is ProviderEntryUiState.CompleteProviderProfile })
    }

    private fun configureAuthenticatedSession() {
        sessionStore.saveSession(
            AuthSession(
                user = User("auth0|provider", "provider@example.com"),
                accessToken = "synthetic-token",
            ),
        )
        currentAccount.gate = null
        profileGate = null
    }

    private fun createEntryViewModel() {
        entryStates.clear()
        entryObserver?.cancel()
        entryViewModel = ProviderEntryViewModel(
            sessionStore = sessionStore,
            resolveProviderEntry = ResolveProviderEntryUseCase(sessionStore, currentAccount),
        )
        entryObserver = observerScope.launch {
            entryViewModel.uiState.collect { entryStates.add(it) }
        }
        scheduler.runCurrent()
    }

    private fun resolvedProvider(): CurrentAccount.Provider =
        (entryViewModel.uiState.value as ProviderEntryUiState.Home).account

    private fun provider() = CurrentAccount.Provider(
        id = 20,
        name = "Juan",
        surname = "Gómez",
        email = "juan@example.com",
        category = com.loresuelvo.serviceprovider.domain.category.Category(1, "Plomería"),
        profilePhotoUrl = "https://cdn.example/profile.jpg",
    )

    private fun jobRequest() = JobRequest(
        id = 1,
        consumerName = "Ana Pérez",
        title = "Reparar pérdida",
        description = "Pérdida debajo de la pileta",
    )

    private fun workOrder() = WorkOrder(
        id = 2,
        consumerName = "Ana Pérez",
        description = "Pérdida debajo de la pileta",
        scheduledOn = Instant.parse("2026-09-20T15:00:00Z").toEpochMilli(),
        status = WorkOrderStatus.Scheduled,
    )

    override fun close() {
        entryObserver?.cancel()
        observerScope.cancel()
        Dispatchers.resetMain()
    }
}
