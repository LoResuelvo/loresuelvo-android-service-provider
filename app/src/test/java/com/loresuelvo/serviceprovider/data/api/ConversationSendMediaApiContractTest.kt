package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.ConfirmedFile
import com.loresuelvo.serviceprovider.domain.file.FilePurpose
import com.loresuelvo.serviceprovider.domain.file.FileRepository
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.PresignUploadResult
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ConversationSendMediaApiContractTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun send_image_walks_presign_upload_confirm_and_posts_with_image_file_ids() =
        runTest {
            // The presign / upload / confirm steps are driven by the
            // in-process FileRepositoryFake, so the MockWebServer only
            // sees the final POST against /conversations/{id}/messages.
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """
                    {
                      "id": 42,
                      "sender_role": "provider",
                      "content": "",
                      "images": [
                        {
                          "id": "file-uuid-1",
                          "url": "https://example.test/image-1.jpg",
                          "original_name": "kitchen.jpg",
                          "mime_type": "image/jpeg"
                        }
                      ],
                      "created_on": "2026-05-30T14:30:00Z"
                    }
                    """.trimIndent(),
                ),
            )

            val outcome = repository().sendMediaMessage(
                conversationId = 42,
                media = listOf(
                    MediaUpload.Image(
                        bytes = byteArrayOf(1, 2, 3, 4),
                        mimeType = "image/jpeg",
                        originalName = "kitchen.jpg",
                    ),
                ),
            )

            val success = outcome as SendMessageOutcome.Success
            assertEquals(42, success.message.id)
            assertEquals(ConversationSender.Provider, success.message.sender)
            val media = success.message.media as com.loresuelvo.serviceprovider.domain.conversation.MediaReference.Image
            assertEquals("file-uuid-1", media.id)
            assertEquals("https://example.test/image-1.jpg", media.url)

            val postRequest = server.takeRequest()
            assertEquals("/conversations/42/messages", postRequest.path)
            assertEquals("POST", postRequest.method)
            val body = postRequest.body.readUtf8()
            assertTrue(
                "expected body to carry image_file_ids, got: $body",
                body.contains("\"image_file_ids\":[\"file-uuid-1\"]"),
            )
        }

    @Test
    fun send_image_maps_presign_Network_failure_to_Network_failure() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(500),
        )

        val outcome = repository().sendMediaMessage(
            conversationId = 42,
            media = listOf(
                MediaUpload.Image(
                    bytes = byteArrayOf(1),
                    mimeType = "image/jpeg",
                    originalName = "x.jpg",
                ),
            ),
        )

        assertTrue(
            "expected Server failure (presign 500), got $outcome",
            outcome is SendMessageOutcome.Failure.Server,
        )
        assertEquals(500, (outcome as SendMessageOutcome.Failure.Server).code)
    }

    @Test
    fun send_image_maps_post_404_to_ConversationNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val outcome = repository().sendMediaMessage(
            conversationId = 42,
            media = listOf(
                MediaUpload.Image(
                    bytes = byteArrayOf(1),
                    mimeType = "image/jpeg",
                    originalName = "x.jpg",
                ),
            ),
        )

        assertTrue(
            "expected ConversationNotFound, got $outcome",
            outcome is SendMessageOutcome.Failure.ConversationNotFound,
        )
    }

    private fun repository(): ApiConversationRepository =
        ApiConversationRepository(api(), FileRepositoryFake())

    private fun api(): BackendApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(SessionStore()))
                .build(),
        )
        .addConverterFactory(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }.asConverterFactory("application/json".toMediaType()),
        )
        .build()
        .create(BackendApi::class.java)

    /**
     * In-process fake that drives the presign → upload → confirm
     * dance against the [MockWebServer]. The upload step writes
     * 200 OK directly; the fake's presign / confirm responses
     * mirror what the backend issues so the repository contract
     * reads the joined ids from the final `POST` request.
     */
    private class FileRepositoryFake : FileRepository {
        override suspend fun presign(
            request: PresignUploadRequest,
        ): PresignUploadOutcome = PresignUploadOutcome.Success(
            result = PresignUploadResult(
                fileId = "file-uuid-1",
                key = "conversation_message_image/file-uuid-1",
                uploadUrl = "https://example.test/upload/file-uuid-1",
                headers = mapOf("Content-Type" to "image/jpeg"),
            ),
        )

        override suspend fun uploadBytes(
            uploadUrl: String,
            headers: Map<String, String>,
            bytes: ByteArray,
        ): UploadBytesOutcome = UploadBytesOutcome.Success

        override suspend fun confirm(
            fileId: String,
            request: ConfirmUploadRequest,
        ): ConfirmUploadOutcome = ConfirmUploadOutcome.Success(
            file = ConfirmedFile(
                id = fileId,
                mimeType = request.mimeType,
                originalName = request.key,
            ),
        )
    }

    private class SessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(
            AuthSession(User("auth0|provider", "provider@example.com"), "synthetic-token"),
        )
        override val sessionFlow: StateFlow<AuthSession?> = state
        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() { state.value = null }
    }
}
