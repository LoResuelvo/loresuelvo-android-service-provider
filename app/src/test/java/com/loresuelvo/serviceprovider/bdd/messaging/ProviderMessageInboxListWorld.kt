package com.loresuelvo.serviceprovider.bdd.messaging

import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessageKind
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationsUseCase
import com.loresuelvo.serviceprovider.ui.screens.messages.MessagesListUiState
import com.loresuelvo.serviceprovider.ui.screens.messages.MessagesListViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ProviderMessageInboxListWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val repository = FakeConversationRepository()
    private lateinit var viewModel: MessagesListViewModel

    init {
        Dispatchers.setMain(dispatcher)
    }

    fun configureConversations() {
        repository.outcome = ConversationsOutcome.Success(
            listOf(
                conversation(id = 2, name = "Ana", message = "Necesito ayuda"),
                conversation(id = 1, name = "Luis", message = "Hola"),
            ),
        )
    }

    fun configurePendingAndActiveConversations() {
        repository.outcome = ConversationsOutcome.Success(
            listOf(
                conversation(id = 2, name = "Ana", message = "Necesito ayuda", status = ConversationStatus.Pending),
                conversation(id = 1, name = "Luis", message = "Hola", status = ConversationStatus.Active),
            ),
        )
    }

    fun configureEmptyConversations() {
        repository.outcome = ConversationsOutcome.Success(emptyList())
    }

    fun configurePendingRequest() {
        repository.pendingCompletion = CompletableDeferred()
    }

    fun loadInbox() {
        viewModel = MessagesListViewModel(GetConversationsUseCase(repository))
        scheduler.advanceUntilIdle()
    }

    fun assertApiOrder() {
        val state = viewModel.uiState.value as MessagesListUiState.Ready
        assertEquals(listOf(2, 1), state.conversations.map { it.id })
    }

    fun assertConsumerIdentity() {
        val state = viewModel.uiState.value as MessagesListUiState.Ready
        assertEquals("Ana", state.conversations.first().counterpart.name)
        assertEquals("Luis", state.conversations.last().counterpart.name)
        assertNotNull(state.conversations.first().counterpart.profilePhotoUrl)
    }

    fun assertPreviewAndTime() {
        val state = viewModel.uiState.value as MessagesListUiState.Ready
        assertEquals("Necesito ayuda", state.conversations.first().lastMessage?.content)
        assertEquals(1_000L, state.conversations.first().updatedOnEpochMillis)
    }

    fun assertPendingConversation() {
        val state = viewModel.uiState.value as MessagesListUiState.Ready
        assertEquals(ConversationStatus.Pending, state.conversations.first().status)
    }

    fun assertActiveConversation() {
        val state = viewModel.uiState.value as MessagesListUiState.Ready
        assertEquals(ConversationStatus.Active, state.conversations.last().status)
    }

    fun assertEmptyState() {
        assertEquals(MessagesListUiState.Ready(emptyList()), viewModel.uiState.value)
    }

    fun assertEmptyStateIsExclusive() {
        val state = viewModel.uiState.value
        assertTrue(state is MessagesListUiState.Ready && state.conversations.isEmpty())
    }

    fun openInboxWhileLoading() {
        viewModel = MessagesListViewModel(GetConversationsUseCase(repository))
        scheduler.runCurrent()
    }

    fun assertLoadingState() {
        assertEquals(MessagesListUiState.Loading, viewModel.uiState.value)
    }

    fun assertLoadingStateIsExclusive() {
        assertEquals(MessagesListUiState.Loading, viewModel.uiState.value)
    }

    override fun close() {
        repository.pendingCompletion?.complete(Unit)
        if (::viewModel.isInitialized) scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private fun conversation(
        id: Int,
        name: String,
        message: String,
        status: ConversationStatus = ConversationStatus.Active,
    ) = Conversation(
        id = id,
        status = status,
        counterpart = ConversationCounterpart(
            id = id + 10,
            name = name,
            surname = "Pérez",
            profilePhotoUrl = if (id == 2) "https://example.test/ana.jpg" else null,
        ),
        lastMessage = ConversationMessage(
            content = message,
            kind = ConversationMessageKind.Text,
            createdOnEpochMillis = 1_000L,
        ),
        updatedOnEpochMillis = 1_000L,
    )

    private class FakeConversationRepository : ConversationRepository {
        var outcome: ConversationsOutcome = ConversationsOutcome.Success(emptyList())
        var pendingCompletion: CompletableDeferred<Unit>? = null

        override suspend fun getConversations(): ConversationsOutcome {
            pendingCompletion?.await()
            return outcome
        }
    }
}
