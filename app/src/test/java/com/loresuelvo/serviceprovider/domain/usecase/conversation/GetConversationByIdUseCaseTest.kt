package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetConversationByIdUseCaseTest {

    @Test
    fun forwards_the_repository_outcome_unchanged() = runTest {
        val expected = ConversationDetailOutcome.Success(
            detail = ConversationDetail(
                id = 42,
                status = com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus.Active,
                counterpart = com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart(
                    id = 7,
                    name = "Ana",
                    surname = "Pérez",
                    profilePhotoUrl = null,
                ),
                messages = emptyList(),
                updatedOnEpochMillis = 1L,
            ),
        )

        val actual = GetConversationByIdUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")

                override suspend fun getConversationById(conversationId: Int) = expected

                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome = error("not exercised")
            },
        )(42)

        assertEquals(expected, actual)
    }

    @Test
    fun rejects_non_positive_conversation_ids() = runTest {
        val useCase = GetConversationByIdUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")

                override suspend fun getConversationById(conversationId: Int) =
                    error("must not reach the repository for invalid ids")

                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome = error("not exercised")
            },
        )

        val thrown = runCatching { useCase(0) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }
}
