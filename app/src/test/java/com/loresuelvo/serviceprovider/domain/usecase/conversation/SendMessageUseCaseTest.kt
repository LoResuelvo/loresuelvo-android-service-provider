package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendMessageUseCaseTest {

    @Test
    fun forwards_the_repository_outcome_unchanged() = runTest {
        val expected = SendMessageOutcome.Success(
            message = ConversationMessage(
                id = 99,
                sender = ConversationSender.Provider,
                content = "Listo para empezar",
                createdOnEpochMillis = 1L,
            ),
        )

        val actual = SendMessageUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")

                override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
                    error("not exercised")

                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome = expected
            },
        )(42, "Listo para empezar")

        assertEquals(expected, actual)
    }

    @Test
    fun trims_the_content_before_dispatching() = runTest {
        var captured: String? = null

        SendMessageUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")

                override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
                    error("not exercised")

                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome {
                    captured = content
                    return SendMessageOutcome.Success(
                        ConversationMessage(
                            id = 1,
                            sender = ConversationSender.Provider,
                            content = content,
                            createdOnEpochMillis = 1L,
                        ),
                    )
                }
            },
        )(42, "  hola  ")

        assertEquals("hola", captured)
    }

    @Test
    fun rejects_blank_content() = runTest {
        val useCase = SendMessageUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")

                override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
                    error("not exercised")

                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome = error("must not reach the repository for blank content")
            },
        )

        assertTrue(runCatching { useCase(42, "   ") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { useCase(42, "") }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun rejects_non_positive_conversation_ids() = runTest {
        val useCase = SendMessageUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")

                override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
                    error("not exercised")

                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome = error("must not reach the repository for invalid ids")
            },
        )

        assertTrue(runCatching { useCase(0, "hola") }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { useCase(-1, "hola") }.exceptionOrNull() is IllegalArgumentException)
    }
}
