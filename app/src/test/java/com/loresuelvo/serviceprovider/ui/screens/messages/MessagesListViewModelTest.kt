package com.loresuelvo.serviceprovider.ui.screens.messages

import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationsUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesListViewModelTest {

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
    fun loads_conversations_in_repository_order() = runTest(scheduler) {
        val repository = FakeConversationRepository(
            ConversationsOutcome.Success(listOf(conversation(2), conversation(1))),
        )
        val viewModel = MessagesListViewModel(GetConversationsUseCase(repository))

        advanceUntilIdle()

        val state = viewModel.uiState.value as MessagesListUiState.Ready
        assertEquals(listOf(2, 1), state.conversations.map { it.id })
        assertEquals(1, repository.calls)
    }

    @Test
    fun retries_after_failure_and_ignores_a_duplicate_retry() = runTest(scheduler) {
        val repository = SequencedConversationRepository(
            listOf(
                ConversationsOutcome.Failure.Network(IllegalStateException("offline")),
                ConversationsOutcome.Success(listOf(conversation(3))),
            ),
        )
        val viewModel = MessagesListViewModel(GetConversationsUseCase(repository))

        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is MessagesListUiState.Error)

        viewModel.load()
        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.uiState.value as MessagesListUiState.Ready
        assertEquals(listOf(3), state.conversations.map { it.id })
        assertEquals(2, repository.calls)
    }

    @Test
    fun ignores_load_while_the_initial_request_is_in_flight() = runTest(scheduler) {
        val completion = CompletableDeferred<Unit>()
        val repository = BlockingConversationRepository(completion)
        val viewModel = MessagesListViewModel(GetConversationsUseCase(repository))

        scheduler.runCurrent()
        viewModel.load()
        scheduler.runCurrent()

        assertEquals(1, repository.calls)
        assertEquals(MessagesListUiState.Loading, viewModel.uiState.value)

        completion.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is MessagesListUiState.Ready)
    }

    private fun conversation(id: Int) = Conversation(
        id = id,
        status = ConversationStatus.Active,
        counterpart = ConversationCounterpart(id, "Ana", "Pérez", null),
        lastMessage = null,
        updatedOnEpochMillis = 1L,
    )

    private class FakeConversationRepository(
        private val outcome: ConversationsOutcome,
    ) : ConversationRepository {
        var calls: Int = 0

        override suspend fun getConversations(): ConversationsOutcome {
            calls += 1
            return outcome
        }

        override suspend fun getConversationById(conversationId: Int) =
            TODO("not exercised by MessagesListViewModelTest")

        override suspend fun sendMessage(
            conversationId: Int,
            content: String,
        ): SendMessageOutcome =
            TODO("not exercised by MessagesListViewModelTest")
    }

    private class SequencedConversationRepository(
        private val outcomes: List<ConversationsOutcome>,
    ) : ConversationRepository {
        var calls: Int = 0

        override suspend fun getConversations(): ConversationsOutcome =
            outcomes.getOrElse(calls++) { outcomes.last() }

        override suspend fun getConversationById(conversationId: Int) =
            TODO("not exercised by MessagesListViewModelTest")

        override suspend fun sendMessage(
            conversationId: Int,
            content: String,
        ): SendMessageOutcome =
            TODO("not exercised by MessagesListViewModelTest")
    }

    private class BlockingConversationRepository(
        private val completion: CompletableDeferred<Unit>,
    ) : ConversationRepository {
        var calls: Int = 0

        override suspend fun getConversations(): ConversationsOutcome {
            calls += 1
            completion.await()
            return ConversationsOutcome.Success(emptyList())
        }

        override suspend fun getConversationById(conversationId: Int) =
            TODO("not exercised by MessagesListViewModelTest")

        override suspend fun sendMessage(
            conversationId: Int,
            content: String,
        ): SendMessageOutcome =
            TODO("not exercised by MessagesListViewModelTest")
    }
}
