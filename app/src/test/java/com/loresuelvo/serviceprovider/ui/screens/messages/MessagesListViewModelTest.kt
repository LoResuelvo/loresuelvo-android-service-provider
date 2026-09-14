package com.loresuelvo.serviceprovider.ui.screens.messages

import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationsUseCase
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
    }
}
