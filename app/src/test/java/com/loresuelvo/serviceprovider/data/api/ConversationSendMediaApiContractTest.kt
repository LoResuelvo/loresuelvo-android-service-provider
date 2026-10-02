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
    fun send_image_maps_post_500_to_Server_failure() = runTest {
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
            "expected Server failure (post 500), got $outcome",
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

    @Test
    fun three_images_confirm_in_order_before_one_post_and_map_every_image() = runTest {
        val files = OrderedFiles()
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{
            "id":99,"sender_role":"consumer","content":"","created_on":"2026-05-30T14:30:00Z",
            "images":[
                {"id":"confirmed-1","url":"https://example.test/1","mime_type":"image/jpeg","original_name":"1.jpg"},
                {"id":"confirmed-2","url":"https://example.test/2","mime_type":"image/jpeg","original_name":"2.jpg"},
                {"id":"confirmed-3","url":"https://example.test/3","mime_type":"image/jpeg","original_name":"3.jpg"}
            ]} """))
        val outcome = ApiConversationRepository(api(), files).sendMediaMessage(42, testImages()) as SendMessageOutcome.Success
        assertEquals((1..3).flatMap { listOf("presign-$it", "put-$it", "confirm-$it") }, files.events)
        assertEquals(listOf("confirmed-1", "confirmed-2", "confirmed-3"), outcome.message.images.map { it.id })
        assertEquals(ConversationSender.Consumer, outcome.message.sender)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/conversations/42/messages", request.path)
        assertTrue(request.body.readUtf8().contains("\"image_file_ids\":[\"confirmed-1\",\"confirmed-2\",\"confirmed-3\"]"))
    }

    @Test
    fun every_upload_stage_failure_stops_before_message_post() = runTest {
        for (stage in listOf("presign", "put", "confirm")) {
            val files = OrderedFiles(failStage = stage)
            val outcome = ApiConversationRepository(api(), files).sendMediaMessage(42, testImages())
            assertTrue(outcome is SendMessageOutcome.Failure.Network)
            assertEquals(0, server.requestCount)
            assertEquals(listOf("presign-1", "put-1", "confirm-1").takeWhile { it != "$stage-1" } + "$stage-1", files.events)
        }
    }

    @Test
    fun invalid_or_mixed_media_never_starts_presign_and_post_failure_is_typed() = runTest {
        val files = OrderedFiles()
        val repository = ApiConversationRepository(api(), files)
        val invalid = listOf(testImages().take(1) + MediaUpload.Audio(byteArrayOf(1), "audio/webm", "a", 1000), List(4) { testImages().first() })
        invalid.forEach { assertTrue(repository.sendMediaMessage(42, it) is SendMessageOutcome.Failure) }
        assertTrue(files.events.isEmpty())
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setResponseCode(503))
        val failure = repository.sendMediaMessage(42, testImages()) as SendMessageOutcome.Failure.Server
        assertEquals(503, failure.code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun audio_json_omits_content_and_other_attachments_using_production_json() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":99,"sender_role":"provider","content":"","created_on":"2026-05-30T14:30:00Z","audio":{"id":"confirmed-1","url":"https://example.test/audio.webm","mime_type":"audio/webm","original_name":"audio.webm","duration_seconds":3}}"""))
        val files = OrderedFiles()
        val outcome = ApiConversationRepository(api(), files).sendMediaMessage(42, listOf(MediaUpload.Audio(byteArrayOf(1), "audio/webm", "audio.webm", 3000)))
        assertTrue(outcome is SendMessageOutcome.Success)
        assertEquals(FilePurpose.CONVERSATION_MESSAGE_AUDIO, files.presigns.single().purpose)
        assertEquals("audio/webm", files.presigns.single().mimeType)
        assertEquals(3000L, ((outcome as SendMessageOutcome.Success).message.media as com.loresuelvo.serviceprovider.domain.conversation.MediaReference.Audio).durationMillis)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/conversations/42/messages", request.path)
        assertEquals("{\"audio_file_id\":\"confirmed-1\"}", request.body.readUtf8())
        assertEquals(listOf("presign-1", "put-1", "confirm-1"), files.events)
    }

    @Test
    fun audio_failures_stop_each_pipeline_and_explicit_retry_reuses_bytes() = runTest {
        val audio = MediaUpload.Audio(byteArrayOf(1, 2), "audio/webm", "audio.webm", 3000)
        for (stage in listOf("presign", "put", "confirm")) {
            val files = OrderedFiles(stage)
            assertTrue(ApiConversationRepository(api(), files).sendMediaMessage(42, listOf(audio)) is SendMessageOutcome.Failure.Network)
            assertEquals(0, server.requestCount)
            assertEquals(listOf("presign-1", "put-1", "confirm-1").takeWhile { it != "$stage-1" } + "$stage-1", files.events)
        }
        val repository = ApiConversationRepository(api(), OrderedFiles())
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(repository.sendMediaMessage(42, listOf(audio)) is SendMessageOutcome.Failure.Server)
        assertTrue(audio.bytes.contentEquals(byteArrayOf(1, 2)))
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"id":99,"sender_role":"provider","content":"","created_on":"2026-05-30T14:30:00Z"}"""))
        assertTrue(repository.sendMediaMessage(42, listOf(audio)) is SendMessageOutcome.Success)
        assertEquals(2, server.requestCount)
    }

    private fun testImages() = (1..3).map { MediaUpload.Image(byteArrayOf(it.toByte()), "image/jpeg", "$it.jpg") }

    private class OrderedFiles(private val failStage: String? = null) : FileRepository {
        val events = mutableListOf<String>()
        val presigns = mutableListOf<PresignUploadRequest>()
        private var index = 0
        override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome {
            index++
            presigns += request
            events += "presign-$index"
            if (failStage == "presign") return PresignUploadOutcome.Failure.Network(java.io.IOException("offline"))
            return PresignUploadOutcome.Success(PresignUploadResult("upload-$index", "key-$index", "https://example.test/$index", emptyMap()))
        }
        override suspend fun uploadBytes(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray): UploadBytesOutcome {
            events += "put-$index"
            if (failStage == "put") return UploadBytesOutcome.Failure.Network(java.io.IOException("offline"))
            return UploadBytesOutcome.Success
        }
        override suspend fun confirm(fileId: String, request: ConfirmUploadRequest): ConfirmUploadOutcome {
            events += "confirm-$index"
            if (failStage == "confirm") return ConfirmUploadOutcome.Failure.Network(java.io.IOException("offline"))
            return ConfirmUploadOutcome.Success(ConfirmedFile(id = "confirmed-$index", mimeType = request.mimeType, originalName = "image"))
        }
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
            com.loresuelvo.serviceprovider.di.NetworkModule.provideJson().asConverterFactory("application/json".toMediaType()),
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
