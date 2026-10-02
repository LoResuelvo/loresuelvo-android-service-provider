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
    fun validates_all_images_formats_count_and_exact_size_before_repository() = runTest {
        var calls = 0
        val repository = object : ConversationRepository {
            override suspend fun getConversations(): ConversationsOutcome = error("Not needed")
            override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome = error("Not needed")
            override suspend fun sendMessage(conversationId: Int, content: String): SendMessageOutcome = error("Not needed")
            override suspend fun sendMediaMessage(conversationId: Int, media: List<MediaUpload>): SendMessageOutcome {
                calls++
                return SendMessageOutcome.Failure.Network(Exception("offline"))
            }
        }
        val useCase = SendMediaMessageUseCase(repository)
        fun image(size: Int = 1, mime: String = "image/jpeg") = MediaUpload.Image(ByteArray(size), mime, "image")
        listOf(
            listOf(image(), image(0)),
            listOf(image(mime = "image/gif")),
            List(4) { image() },
            listOf(image(5 * 1024 * 1024 + 1)),
            listOf(image(), MediaUpload.Audio(byteArrayOf(1), "audio/webm", "clip", 1000)),
        ).forEach { assertTrue(useCase(42, it) is SendMessageOutcome.Failure) }
        assertEquals(0, calls)
        useCase(42, listOf(image(5 * 1024 * 1024), image(mime = "image/png"), image(mime = "image/webp")))
        assertEquals(1, calls)
    }

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
