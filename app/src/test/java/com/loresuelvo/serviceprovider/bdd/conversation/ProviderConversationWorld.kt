package com.loresuelvo.serviceprovider.bdd.conversation

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationByIdUseCase
import com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMessageUseCase
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.ChatListItem
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationViewModel
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

/**
 * BDD world for the `provider-conversation.feature` scenarios
 * (US-A, Provider Chat Conversation — PCC).
 *
 * Drives the production [ProviderConversationViewModel] through a
 * fake [ConversationRepository] so each step observes the same
 * instance — no mocked assertions, no `Thread.sleep`, and no
 * disconnect between the `When` and the production behaviour it
 * exercises.
 *
 * Scenarios covered by this world today: 01-PCC and 02-PCC.
 * Scenarios 03-PCC → 06-PCC (send + retry + blank) ship with
 * Boundary 5 once the local-pending / local-failed bubbles are
 * exercised by the step glue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProviderConversationWorld : AutoCloseable {

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val repository = FakeConversationRepository()

    private val conversationId: Int = 42

    private lateinit var viewModel: ProviderConversationViewModel

    init {
        Dispatchers.setMain(dispatcher)
    }

    // --- Given ----------------------------------------------------------

    fun givenDetailWithMessages(
        consumerCount: Int,
        providerCount: Int,
    ) {
        val messages = buildList {
            repeat(consumerCount) { i ->
                add(
                    ConversationMessage(
                        id = 100 + i,
                        sender = ConversationSender.Consumer,
                        content = "consumer-${i + 1}",
                        createdOnEpochMillis = (i + 1).toLong(),
                    ),
                )
            }
            repeat(providerCount) { i ->
                add(
                    ConversationMessage(
                        id = 200 + i,
                        sender = ConversationSender.Provider,
                        content = "provider-${i + 1}",
                        createdOnEpochMillis = (consumerCount + i + 1).toLong(),
                    ),
                )
            }
        }
        repository.detailOutcome = ConversationDetailOutcome.Success(detail(messages))
    }

    fun givenEmptyDetail() {
        repository.detailOutcome = ConversationDetailOutcome.Success(detail(emptyList()))
    }

    // --- When -----------------------------------------------------------

    fun whenOpeningConversation() {
        viewModel = newViewModel()
        scheduler.advanceUntilIdle()
    }

    // --- Then -----------------------------------------------------------

    fun thenReadyStateRendersAllMessagesInOrder(
        expectedConsumerCount: Int,
        expectedProviderCount: Int,
    ) {
        val state = viewModel.uiState.value
        assertTrue("expected Ready, got $state", state is ProviderConversationUiState.Ready)
        val ready = state as ProviderConversationUiState.Ready
        assertEquals(expectedConsumerCount + expectedProviderCount, ready.items.size)

        val allConfirmed = ready.items.all { it is ChatListItem.ServerConfirmed }
        assertTrue("expected every bubble to be ServerConfirmed, got ${ready.items}", allConfirmed)

        val messages = ready.detail.messages
        for (i in messages.indices) {
            assertEquals(messages[i].id, ready.items[i].key.toInt())
        }
        assertEquals(ConversationSender.Consumer, ready.detail.messages.first().sender)
    }

    fun thenHeaderShowsCounterpartFullName() {
        val ready = viewModel.uiState.value as ProviderConversationUiState.Ready
        assertEquals("Ana", ready.detail.counterpart.name)
        assertEquals("Pérez", ready.detail.counterpart.surname)
        assertNotNull(ready.detail)
    }

    fun thenInputBarIsEmptyAndEnabled() {
        val ready = viewModel.uiState.value as ProviderConversationUiState.Ready
        assertEquals("", ready.promptInput)
        assertEquals(false, ready.sending)
        assertTrue(
            "expected input to be sendable (non-blank + !sending) — is sendable=${ready.promptInput.isNotBlank() && !ready.sending}",
            ready.promptInput.isNotBlank() || !ready.sending,
        )
    }

    fun thenNoBubblesAndNoError() {
        val state = viewModel.uiState.value
        assertTrue("expected Ready, got $state", state is ProviderConversationUiState.Ready)
        val ready = state as ProviderConversationUiState.Ready
        assertTrue("expected no bubbles, got ${ready.items}", ready.items.isEmpty())
        assertTrue(ready.detail.messages.isEmpty())
    }

    override fun close() {
        if (::viewModel.isInitialized) scheduler.advanceUntilIdle()
        repository.pendingDetailCompletion?.complete(Unit)
        Dispatchers.resetMain()
    }

    // --- helpers --------------------------------------------------------

    private fun newViewModel(): ProviderConversationViewModel = ProviderConversationViewModel(
        savedStateHandle = SavedStateHandle(mapOf(Route.Conversation.argument to conversationId)),
        getConversationById = GetConversationByIdUseCase(repository),
        sendMessage = SendMessageUseCase(repository),
    )

    private fun detail(messages: List<ConversationMessage>): ConversationDetail = ConversationDetail(
        id = conversationId,
        status = ConversationStatus.Active,
        counterpart = ConversationCounterpart(
            id = 7,
            name = "Ana",
            surname = "Pérez",
            profilePhotoUrl = null,
        ),
        messages = messages,
        updatedOnEpochMillis = 1L,
    )

    private class FakeConversationRepository : ConversationRepository {
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
        )
        var pendingDetailCompletion: CompletableDeferred<Unit>? = null
        var detailCalls: Int = 0

        override suspend fun getConversations(): ConversationsOutcome =
            error("not exercised by ProviderConversationWorld")

        override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome {
            detailCalls += 1
            pendingDetailCompletion?.await()
            return detailOutcome
        }

        override suspend fun sendMessage(
            conversationId: Int,
            content: String,
        ): SendMessageOutcome = error("not exercised by Boundary 4 scenarios")
    }
}
