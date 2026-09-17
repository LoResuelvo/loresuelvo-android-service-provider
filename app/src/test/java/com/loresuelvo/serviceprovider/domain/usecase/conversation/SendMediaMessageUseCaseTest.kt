package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendMediaMessageUseCaseTest {

    @Test
    fun rejects_an_empty_payload_with_a_typed_Server_failure() = runTest {
        val useCase = SendMediaMessageUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")
                override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
                    error("not exercised")
                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome = error("not exercised")
                override suspend fun sendMediaMessage(
                    conversationId: Int,
                    media: List<MediaUpload>,
                ): SendMessageOutcome = error("must not reach the repository for empty payload")
            },
        )

        val outcome = useCase(conversationId = 42, media = emptyList())
        assertTrue("expected Server failure, got $outcome", outcome is SendMessageOutcome.Failure.Server)
        assertEquals(0, (outcome as SendMessageOutcome.Failure.Server).code)
    }

    @Test
    fun rejects_a_payload_of_empty_bytes() = runTest {
        val useCase = SendMediaMessageUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")
                override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
                    error("not exercised")
                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome = error("not exercised")
                override suspend fun sendMediaMessage(
                    conversationId: Int,
                    media: List<MediaUpload>,
                ): SendMessageOutcome = error("must not reach the repository for empty bytes")
            },
        )

        val outcome = useCase(
            conversationId = 42,
            media = listOf(
                MediaUpload.Image(
                    bytes = ByteArray(0),
                    mimeType = "image/jpeg",
                    originalName = "empty.jpg",
                ),
            ),
        )
        assertTrue(outcome is SendMessageOutcome.Failure.Server)
    }

    @Test
    fun forwards_non_empty_payload_to_the_repository() = runTest {
        val expected = SendMessageOutcome.Failure.Network(
            cause = RuntimeException("offline"),
        )
        val useCase = SendMediaMessageUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")
                override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
                    error("not exercised")
                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome = error("not exercised")
                override suspend fun sendMediaMessage(
                    conversationId: Int,
                    media: List<MediaUpload>,
                ): SendMessageOutcome = expected
            },
        )

        val outcome = useCase(
            conversationId = 42,
            media = listOf(
                MediaUpload.Image(
                    bytes = byteArrayOf(1, 2, 3),
                    mimeType = "image/jpeg",
                    originalName = "kitchen.jpg",
                ),
            ),
        )
        assertEquals(expected, outcome)
    }

    @Test
    fun rejects_non_positive_conversation_ids() = runTest {
        val useCase = SendMediaMessageUseCase(
            object : ConversationRepository {
                override suspend fun getConversations(): ConversationsOutcome =
                    error("not exercised")
                override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome =
                    error("not exercised")
                override suspend fun sendMessage(
                    conversationId: Int,
                    content: String,
                ): SendMessageOutcome = error("not exercised")
                override suspend fun sendMediaMessage(
                    conversationId: Int,
                    media: List<MediaUpload>,
                ): SendMessageOutcome = error("must not reach the repository for invalid ids")
            },
        )

        assertTrue(
            runCatching {
                useCase(
                    conversationId = 0,
                    media = listOf(
                        MediaUpload.Image(
                            bytes = byteArrayOf(1),
                            mimeType = "image/jpeg",
                            originalName = "x.jpg",
                        ),
                    ),
                )
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }
}
