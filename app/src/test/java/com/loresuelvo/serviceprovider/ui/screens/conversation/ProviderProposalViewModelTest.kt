package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.usecase.proposal.ValidateServiceProposalUseCase
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.proposal.CreateServiceProposalUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

class ProviderProposalViewModelTest {
    private val viewModelStore = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { viewModelStore.clear(); Dispatchers.resetMain() }
    private val validator = ValidateServiceProposalUseCase()
    private val sessionStore = object : AuthSessionStore {
        override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider-1", "provider@example.com"), "token"))
        override fun getSession() = sessionFlow.value
        override fun saveSession(session: AuthSession) { sessionFlow.value = session }
        override fun clearSession() { sessionFlow.value = null }
    }
    private val clock = object : ProposalTimeSource() {
        override fun nowMillis() = 1_780_000_000_000L
        override fun zone() = java.util.TimeZone.getTimeZone("UTC")
    }
    @Test fun `confirmed pending creation clears draft and emits success once`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
        val calls = mutableListOf<ValidatedServiceProposal>()
        val repository = object : ServiceProposalRepository {
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
                calls += proposal
                return CreateServiceProposalOutcome.Created(9)
            }
        }
        val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
        val viewModel = ProviderProposalViewModel(handle, validator, clock, CreateServiceProposalUseCase(repository), sessionStore)
            .also { viewModelStore.put("proposal", it) }
        viewModel.open(detail(42, 7, ConversationStatus.Active))
        viewModel.updateAmount("100,50")
        viewModel.updateDate("2026-10-01")
        viewModel.updateTime("10:00")
        viewModel.updateReason("Inspect sink")
        viewModel.selectDuration(45)
        assertTrue(viewModel.continueToConfirmation())

        viewModel.confirmSend()
        viewModel.confirmSend()
        testScheduler.advanceUntilIdle()

        assertEquals(1, calls.size)
        assertEquals(7, calls.single().consumerId)
        assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
        assertTrue(viewModel.consumeSuccess())
        assertFalse(viewModel.consumeSuccess())
        assertTrue(viewModel.open(detail(42, 7, ConversationStatus.Active)))
        assertEquals("", (viewModel.uiState.value as ProposalUiState.Form).amount)
        assertEquals(null, handle.get<String>("proposal_amount"))
        } finally { viewModelStore.clear(); Dispatchers.resetMain() }
    }

    @Test fun `sending blocks duplicate confirmation and dismissal`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val result = CompletableDeferred<CreateServiceProposalOutcome>()
            var calls = 0
            val repository = object : ServiceProposalRepository {
                override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
                    calls++
                    return result.await()
                }
            }
            val viewModel = filledViewModel(CreateServiceProposalUseCase(repository))
            assertTrue(viewModel.continueToConfirmation())
            viewModel.confirmSend()
            viewModel.confirmSend()
            viewModel.close()
            assertFalse(viewModel.open(detail(42, 7, ConversationStatus.Active)))
            testScheduler.runCurrent()
            assertEquals(1, calls)
            assertTrue(viewModel.uiState.value is ProposalUiState.Sending)
            result.complete(CreateServiceProposalOutcome.Created(9))
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
        } finally { viewModelStore.clear(); Dispatchers.resetMain() }
    }

    @Test fun `restored draft keeps its form and consumer without sending`() {
        val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
        val first = filledViewModel(createUseCase, handle = handle)
        first.selectDuration(null)
        first.updateCustomDuration("75")
        first.selectOffset(0)
        val original = first.uiState.value
        viewModelStore.clear()

        val restored = ProviderProposalViewModel(handle, validator, clock, createUseCase, sessionStore)
        assertFalse(restored.open(detail(43, 8, ConversationStatus.Active)))
        assertTrue(restored.restore(detail(42, 7, ConversationStatus.Active)))
        assertEquals(original, restored.uiState.value)
    }

    @Test fun `closed review reopens and restores as an editable form`() {
        val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
        val chat = detail(42, 7, ConversationStatus.Active)
        val first = filledViewModel(createUseCase, handle = handle)
        assertTrue(first.continueToConfirmation())
        first.close()
        assertTrue(first.open(chat))
        val reopened = first.uiState.value as ProposalUiState.Form
        viewModelStore.clear()

        val restored = ProviderProposalViewModel(handle, validator, clock, createUseCase, sessionStore)
        assertTrue(restored.restore(chat))
        assertEquals(reopened, restored.uiState.value)
    }

    @Test fun `restored interrupted send requires acknowledgement and never posts automatically`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
            val pending = CompletableDeferred<CreateServiceProposalOutcome>()
            var calls = 0
            val repository = object : ServiceProposalRepository {
                override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
                    calls++
                    return pending.await()
                }
            }
            val create = CreateServiceProposalUseCase(repository)
            val first = filledViewModel(create, handle = handle)
            assertTrue(first.continueToConfirmation())
            first.confirmSend()
            testScheduler.runCurrent()
            assertEquals(1, calls)
            viewModelStore.clear()

            val restored = ProviderProposalViewModel(handle, validator, clock, create, sessionStore)
            assertTrue(restored.restore(detail(42, 7, ConversationStatus.Active)))
            val review = restored.uiState.value as ProposalUiState.Reviewing
            assertEquals(CreateServiceProposalOutcome.Failure.Uncertain, review.failure)
            assertEquals("100", review.form.amount)
            testScheduler.runCurrent()
            restored.confirmSend()
            testScheduler.runCurrent()
            assertEquals(1, calls)
            pending.cancel()
        } finally { viewModelStore.clear(); Dispatchers.resetMain() }
    }

    @Test fun `retained ViewModel keeps one pending send across background and rotation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val pending = CompletableDeferred<CreateServiceProposalOutcome>()
            var calls = 0
            val repository = object : ServiceProposalRepository {
                override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
                    calls++
                    return pending.await()
                }
            }
            val viewModel = filledViewModel(CreateServiceProposalUseCase(repository))
            assertTrue(viewModel.continueToConfirmation())
            viewModel.confirmSend()
            testScheduler.runCurrent()
            val retained = viewModelStore["proposal"] as ProviderProposalViewModel
            assertTrue(retained.uiState.value is ProposalUiState.Sending)
            retained.confirmSend()
            testScheduler.runCurrent()
            assertEquals(1, calls)
            pending.complete(CreateServiceProposalOutcome.Created(9))
            testScheduler.advanceUntilIdle()
            assertTrue(retained.uiState.value is ProposalUiState.Closed)
        } finally { viewModelStore.clear(); Dispatchers.resetMain() }
    }

    @Test fun `session change discards visible draft`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
            val viewModel = filledViewModel(createUseCase, handle = handle)
            sessionStore.saveSession(AuthSession(User("provider-2", "other@example.com"), "other-token"))
            testScheduler.runCurrent()
            assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
            assertFalse(handle.contains("proposal_amount"))
            assertFalse(viewModel.restore(detail(42, 7, ConversationStatus.Active)))
        } finally { viewModelStore.clear(); Dispatchers.resetMain() }
    }

    @Test fun `final confirmation revalidates time before any POST`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var now = clock.nowMillis()
            var calls = 0
            val source = object : ProposalTimeSource() {
                override fun nowMillis() = now
                override fun zone() = java.util.TimeZone.getTimeZone("UTC")
            }
            val repository = object : ServiceProposalRepository {
                override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
                    calls++
                    return CreateServiceProposalOutcome.Created(9)
                }
            }
            val viewModel = filledViewModel(CreateServiceProposalUseCase(repository), source)
            assertTrue(viewModel.continueToConfirmation())
            now = 1_791_000_000_000L
            viewModel.confirmSend()
            assertEquals(0, calls)
            assertTrue(ProposalValidationError.LeadTime in (viewModel.uiState.value as ProposalUiState.Form).errors)
        } finally { viewModelStore.clear(); Dispatchers.resetMain() }
    }

    @Test fun `uncertain result retains draft and requires explicit duplicate risk acknowledgement`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val repository = object : ServiceProposalRepository {
                override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
                    calls++
                    return CreateServiceProposalOutcome.Failure.Uncertain
                }
            }
            val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
            val viewModel = filledViewModel(CreateServiceProposalUseCase(repository), handle = handle)
            assertTrue(viewModel.continueToConfirmation())
            viewModel.confirmSend()
            testScheduler.advanceUntilIdle()
            assertEquals(1, calls)
            assertEquals(true, handle.get<Boolean>("proposal_send_uncertain"))
            viewModel.confirmSend()
            testScheduler.advanceUntilIdle()
            assertEquals(1, calls)
            viewModel.acknowledgeDuplicateRisk()
            viewModel.confirmSend()
            testScheduler.advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals("100", (viewModel.uiState.value as ProposalUiState.Reviewing).form.amount)
        } finally { viewModelStore.clear(); Dispatchers.resetMain() }
    }

    @Test fun `inactive conversation rejection blocks blind resend in the same entry`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val repository = object : ServiceProposalRepository {
                override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome {
                    calls++
                    return CreateServiceProposalOutcome.Failure.InactiveConversation
                }
            }
            val viewModel = filledViewModel(CreateServiceProposalUseCase(repository))
            assertTrue(viewModel.continueToConfirmation())
            viewModel.confirmSend()
            testScheduler.advanceUntilIdle()
            viewModel.cancelReview()
            assertTrue(viewModel.continueToConfirmation())
            viewModel.confirmSend()
            testScheduler.advanceUntilIdle()
            assertEquals(1, calls)
            assertEquals(CreateServiceProposalOutcome.Failure.InactiveConversation,
                (viewModel.uiState.value as ProposalUiState.Reviewing).failure)
        } finally { viewModelStore.clear(); Dispatchers.resetMain() }
    }

    private fun filledViewModel(
        create: CreateServiceProposalUseCase,
        source: ProposalTimeSource = clock,
        handle: SavedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
    ): ProviderProposalViewModel = ProviderProposalViewModel(handle, validator, source, create, sessionStore).also {
        viewModelStore.put("proposal", it)
    }.apply {
        open(detail(42, 7, ConversationStatus.Active))
        updateAmount("100")
        updateDate("2026-10-01")
        updateTime("10:00")
        updateReason("Inspect sink")
        selectDuration(45)
    }
    private val createUseCase = CreateServiceProposalUseCase(object : ServiceProposalRepository {
        override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
            error("Unexpected proposal creation")
    })

    @Test
    fun `active chat opens a form for its consumer without confusing the IDs`() {
        val viewModel = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
            validator, clock, createUseCase, sessionStore,
        )
        viewModel.open(detail(42, 7, ConversationStatus.Active))

        val form = viewModel.uiState.value as ProposalUiState.Form
        assertEquals(42, form.conversationId)
        assertEquals(7, form.consumerId)
        assertEquals("Ana Pérez", form.consumerName)
        assertEquals("", form.amount)
        assertEquals("", form.date)
        assertEquals("", form.time)
        assertEquals("", form.reason)
        assertEquals("", form.duration)
    }

    @Test
    fun `rejects a detail from another chat and nonactive states`() {
        val viewModel = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
            validator, clock, createUseCase, sessionStore,
        )
        assertFalse(viewModel.open(detail(43, 7, ConversationStatus.Active)))
        assertFalse(viewModel.open(detail(42, 7, ConversationStatus.Pending)))
        assertFalse(viewModel.open(detail(42, 7, ConversationStatus.Rejected)))
        assertFalse(viewModel.open(detail(42, 7, ConversationStatus.Unsupported("other"))))
        assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
    }

    @Test
    fun `editing persists across close and duration can switch between preset and custom`() {
        val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
        val viewModel = ProviderProposalViewModel(handle, validator, clock, createUseCase, sessionStore)
        val chat = detail(42, 7, ConversationStatus.Active)
        viewModel.open(chat)
        viewModel.updateAmount("100")
        viewModel.updateDate("2026-09-25")
        viewModel.updateTime("10:00")
        viewModel.updateReason("Inspect the sink")
        viewModel.selectDuration(45)
        assertEquals("45", (viewModel.uiState.value as ProposalUiState.Form).duration)
        viewModel.selectDuration(null)
        viewModel.updateCustomDuration("75")
        viewModel.selectOffset(0)
        viewModel.close()
        assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
        assertFalse(viewModel.open(detail(43, 8, ConversationStatus.Active)))
        assertTrue(viewModel.uiState.value is ProposalUiState.Closed)
        viewModel.open(chat)
        val form = viewModel.uiState.value as ProposalUiState.Form
        assertEquals("100", form.amount)
        assertEquals("2026-09-25", form.date)
        assertEquals("10:00", form.time)
        assertEquals("Inspect the sink", form.reason)
        assertEquals("75", form.duration)
        assertTrue(form.customDuration)
        assertEquals("UTC", form.zoneId)
        assertEquals(0, form.selectedOffsetMinutes)
        assertEquals("75", handle.get<String>("proposal_duration"))
    }

    @Test fun `leaving the chat creates a fresh proposal entry`() {
        val first = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)), validator, clock, createUseCase, sessionStore)
        first.open(detail(42, 7, ConversationStatus.Active))
        first.updateAmount("100")
        first.close()

        val next = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 43)), validator, clock, createUseCase, sessionStore)
        assertTrue(next.open(detail(43, 8, ConversationStatus.Active)))
        assertEquals("", (next.uiState.value as ProposalUiState.Form).amount)
    }

    @Test fun `invalid amount stays in form until corrected`() {
        val viewModel = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)), validator, clock, createUseCase, sessionStore)
        viewModel.open(detail(42, 7, ConversationStatus.Active))
        viewModel.updateAmount("0")
        viewModel.updateDate("2026-10-01")
        viewModel.updateTime("10:00")
        viewModel.updateReason("Inspect sink")
        viewModel.selectDuration(45)

        assertFalse(viewModel.continueToConfirmation())
        assertEquals(setOf(ProposalValidationError.Amount),
            (viewModel.uiState.value as ProposalUiState.Form).errors)
        viewModel.updateAmount("100,50")
        assertTrue(viewModel.continueToConfirmation())
        assertEquals(emptySet<ProposalValidationError>(),
            (viewModel.uiState.value as ProposalUiState.Reviewing).form.errors)
    }

    @Test fun `captured draft zone survives source zone change and reopening`() {
        var currentZone = java.util.TimeZone.getTimeZone("America/New_York")
        val source = object : ProposalTimeSource() {
            override fun nowMillis() = clock.nowMillis()
            override fun zone() = currentZone
        }
        val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
        val viewModel = ProviderProposalViewModel(handle, validator, source, createUseCase, sessionStore)
        val chat = detail(42, 7, ConversationStatus.Active)
        viewModel.open(chat)
        currentZone = java.util.TimeZone.getTimeZone("UTC")
        viewModel.close()
        viewModel.open(chat)
        assertEquals("America/New_York", (viewModel.uiState.value as ProposalUiState.Form).zoneId)
        assertEquals("America/New_York", handle.get<String>("proposal_zone_id"))
    }

    @Test fun `valid draft opens review with normalized details before any send`() {
        val viewModel = ProviderProposalViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)), validator, clock, createUseCase, sessionStore)
        viewModel.open(detail(42, 7, ConversationStatus.Active))
        viewModel.updateAmount("100,50")
        viewModel.updateDate("2026-10-01")
        viewModel.updateTime("10:00")
        viewModel.updateReason("  Inspect sink  ")
        viewModel.selectDuration(45)

        assertTrue(viewModel.continueToConfirmation())
        val review = viewModel.uiState.value as ProposalUiState.Reviewing
        assertEquals("Ana Pérez", review.form.consumerName)
        assertEquals(7, review.proposal.consumerId)
        assertEquals("100.5", review.proposal.amountPesos)
        assertEquals("Inspect sink", review.proposal.reason)
        assertEquals(45, review.proposal.durationMinutes)
        assertEquals("UTC", review.form.zoneId)
    }

    @Test fun `cancel review preserves custom draft and allows further editing`() {
        val handle = SavedStateHandle(mapOf(Route.Conversation.argument to 42))
        val viewModel = ProviderProposalViewModel(handle, validator, clock, createUseCase, sessionStore)
        viewModel.open(detail(42, 7, ConversationStatus.Active))
        viewModel.updateAmount("100,50")
        viewModel.updateDate("2026-10-01")
        viewModel.updateTime("10:00")
        viewModel.updateReason("Inspect sink")
        viewModel.selectDuration(null)
        viewModel.updateCustomDuration("75")
        viewModel.selectOffset(0)
        assertTrue(viewModel.continueToConfirmation())
        val original = (viewModel.uiState.value as ProposalUiState.Reviewing).form

        viewModel.cancelReview()
        assertEquals(original, viewModel.uiState.value)
        viewModel.updateReason("Repair sink")
        assertEquals(original.copy(reason = "Repair sink"), viewModel.uiState.value)
        assertEquals("75", handle.get<String>("proposal_duration"))
        assertEquals(0, handle.get<Int>("proposal_offset_minutes"))
    }

    private fun detail(conversationId: Int, consumerId: Int, status: ConversationStatus) =
        ConversationDetail(
            id = conversationId,
            status = status,
            counterpart = ConversationCounterpart(consumerId, "Ana", "Pérez", null),
            messages = emptyList(),
            updatedOnEpochMillis = 1L,
        )
}
