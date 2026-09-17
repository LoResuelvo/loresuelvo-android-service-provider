package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationByIdUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMediaMessageUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMessageUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderConversationViewModelTest {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_emits_Ready_with_server_confirmed_messages() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(
            messages = listOf(
                message(id = 1, sender = ConversationSender.Consumer, content = "Hola"),
                message(id = 2, sender = ConversationSender.Provider, content = "Listo"),
            ),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        val ready = readyState(vm)
        assertEquals(2, ready.items.size)
        assertTrue(ready.items[0] is ChatListItem.ServerConfirmed)
        assertTrue(ready.items[1] is ChatListItem.ServerConfirmed)
        assertEquals("Hola", ready.items[0].content)
        assertEquals("", ready.promptInput)
        assertEquals(false, ready.sending)
    }

    @Test
    fun load_emits_Error_on_network_failure() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = ConversationDetailOutcome.Failure.Network(
            cause = RuntimeException("offline"),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        val error = vm.uiState.value as ProviderConversationUiState.Error
        assertTrue(
            "expected Network failure, got ${error.failure}",
            error.failure is ConversationDetailOutcome.Failure.Network,
        )
    }

    @Test
    fun load_emits_Ready_with_empty_messages_for_brand_new_conversation() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val vm = viewModel(repo)
        advanceUntilIdle()

        val ready = readyState(vm)
        assertTrue(ready.items.isEmpty())
    }

    @Test
    fun onSendClick_appends_pending_bubble_and_replaces_with_server_confirmed_on_success() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            repo.sendOutcome = SendMessageOutcome.Success(
                message = message(
                    id = 99,
                    sender = ConversationSender.Provider,
                    content = "Listo para empezar",
                ),
            )
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.onPromptChange("Listo para empezar")
            vm.onSendClick()
            advanceUntilIdle()

            val ready = readyState(vm)
            assertEquals(1, ready.items.size)
            val resolved = ready.items[0]
            assertTrue(
                "expected ServerConfirmed, got $resolved",
                resolved is ChatListItem.ServerConfirmed,
            )
            assertEquals(99, (resolved as ChatListItem.ServerConfirmed).message.id)
            assertEquals("", ready.promptInput)
            assertEquals(false, ready.sending)
        }

    @Test
    fun onSendClick_replaces_pending_with_failed_bubble_on_network_failure() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            repo.sendOutcome = SendMessageOutcome.Failure.Network(
                cause = RuntimeException("offline"),
            )
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.onPromptChange("Mañana a las 10")
            vm.onSendClick()
            advanceUntilIdle()

            val ready = readyState(vm)
            assertEquals(1, ready.items.size)
            val resolved = ready.items[0]
            assertTrue(
                "expected LocalFailed, got $resolved",
                resolved is ChatListItem.LocalFailed,
            )
            assertEquals(
                "Mañana a las 10",
                (resolved as ChatListItem.LocalFailed).pendingPrompt,
            )
            assertEquals(false, ready.sending)
        }

    @Test
    fun onSendClick_is_a_noop_when_prompt_is_blank() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onPromptChange("   ")
        vm.onSendClick()
        advanceUntilIdle()

        val ready = readyState(vm)
        assertTrue(ready.items.isEmpty())
        assertEquals(false, ready.sending)
        assertEquals(0, repo.sendCalls)
    }

    @Test
    fun onSendClick_is_a_noop_when_already_sending() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = detailOutcome(messages = emptyList())
        val gate = CompletableDeferred<Unit>()
        repo.sendOutcome = SendMessageOutcome.Success(
            message = message(id = 1, sender = ConversationSender.Provider, content = "x"),
        )
        repo.sendGate = gate
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onPromptChange("hola")
        vm.onSendClick()
        advanceUntilIdle()
        // First send is now in flight (waiting on the gate).
        assertEquals(true, readyState(vm).sending)

        vm.onPromptChange("chau")
        vm.onSendClick()
        advanceUntilIdle()

        // Second send did not fire because sending=true.
        assertEquals(1, repo.sendCalls)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun onRetrySendFailedBubble_resubmits_and_replaces_with_server_confirmed() =
        runTest(scheduler) {
            val repo = RecordingRepository()
            repo.detailOutcome = detailOutcome(messages = emptyList())
            repo.sendOutcome = SendMessageOutcome.Failure.Network(
                cause = RuntimeException("offline"),
            )
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.onPromptChange("Mañana a las 10")
            vm.onSendClick()
            advanceUntilIdle()

            val failedKey = readyState(vm).items
                .filterIsInstance<ChatListItem.LocalFailed>()
                .single()
                .key

            repo.sendOutcome = SendMessageOutcome.Success(
                message = message(
                    id = 7,
                    sender = ConversationSender.Provider,
                    content = "Mañana a las 10",
                ),
            )
            vm.onRetrySendFailedBubble(failedKey)
            advanceUntilIdle()

            val ready = readyState(vm)
            assertEquals(1, ready.items.size)
            val resolved = ready.items[0]
            assertTrue(resolved is ChatListItem.ServerConfirmed)
            assertEquals(7, (resolved as ChatListItem.ServerConfirmed).message.id)
            assertEquals(false, ready.sending)
        }

    @Test
    fun onRetryLoad_replays_load_after_a_failed_initial_fetch() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = ConversationDetailOutcome.Failure.Network(
            cause = RuntimeException("offline"),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertTrue(vm.uiState.value is ProviderConversationUiState.Error)

        repo.detailOutcome = detailOutcome(messages = emptyList())
        vm.onRetryLoad()
        advanceUntilIdle()

        val ready = readyState(vm)
        assertTrue(ready.items.isEmpty())
    }

    @Test
    fun onPromptChange_is_a_noop_outside_Ready() = runTest(scheduler) {
        val repo = RecordingRepository()
        repo.detailOutcome = ConversationDetailOutcome.Failure.Network(
            cause = RuntimeException("offline"),
        )
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.onPromptChange("hola")
        advanceUntilIdle()

        // Still in Error state — the prompt change was ignored.
        assertTrue(vm.uiState.value is ProviderConversationUiState.Error)
    }

    // --- helpers --------------------------------------------------------

    private fun viewModel(repo: RecordingRepository): ProviderConversationViewModel =
        ProviderConversationViewModel(
            savedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
            getConversationById = GetConversationByIdUseCase(repo),
            sendMessage = SendMessageUseCase(repo),
            sendMediaMessage = SendMediaMessageUseCase(repo),
            mediaReader = NotExercisedMediaReader,
        )

    private fun readyState(vm: ProviderConversationViewModel): ProviderConversationUiState.Ready {
        val state = vm.uiState.value
        assertTrue(
            "expected Ready, got $state",
            state is ProviderConversationUiState.Ready,
        )
        return state as ProviderConversationUiState.Ready
    }

    private fun detailOutcome(
        messages: List<ConversationMessage>,
    ): ConversationDetailOutcome.Success = ConversationDetailOutcome.Success(
        detail = ConversationDetail(
            id = 42,
            status = ConversationStatus.Active,
            counterpart = ConversationCounterpart(
                id = 7,
                name = "Ana",
                surname = "Pérez",
                profilePhotoUrl = null,
            ),
            messages = messages,
            updatedOnEpochMillis = 1L,
        ),
    )

    private fun message(id: Int, sender: ConversationSender, content: String) = ConversationMessage(
        id = id,
        sender = sender,
        content = content,
        createdOnEpochMillis = id.toLong(),
    )

    private object NotExercisedMediaReader : com.loresuelvo.serviceprovider.data.media.MediaReader {
        override suspend fun read(uri: android.net.Uri): com.loresuelvo.serviceprovider.domain.conversation.MediaUpload =
            error("MediaReader is not exercised by US-A VM tests")
    }

    private class RecordingRepository(
        var detailOutcome: ConversationDetailOutcome = ConversationDetailOutcome.Success(
            detail = ConversationDetail(
                id = 42,
                status = ConversationStatus.Active,
                counterpart = ConversationCounterpart(
                    id = 7,
                    name = "Ana",
                    surname = "Pérez",
                    profilePhotoUrl = null,
                ),
                messages = emptyList(),
                updatedOnEpochMillis = 1L,
            ),
        ),
        var sendOutcome: SendMessageOutcome =
            SendMessageOutcome.Failure.Network(cause = RuntimeException("not set")),
        var sendGate: CompletableDeferred<Unit>? = null,
    ) : ConversationRepository {
        var sendCalls: Int = 0

        override suspend fun getConversations(): ConversationsOutcome =
            error("not exercised by ProviderConversationViewModelTest")

        override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
            detailOutcome

        override suspend fun sendMessage(
            conversationId: Int,
            content: String,
        ): SendMessageOutcome {
            sendCalls += 1
            sendGate?.await()
            return sendOutcome
        }
    }
}
