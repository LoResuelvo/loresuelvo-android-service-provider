package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.data.api.upload.FileUploader
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.FilePurpose
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class ApiFileRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ApiFileRepository
    private val stubUploader = RecordingFileUploader()
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        val backendApi = retrofit.create(BackendApi::class.java)
        repository = ApiFileRepository(
            backendApi = backendApi,
            fileUploader = stubUploader,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun presign_posts_snake_case_payload_and_maps_response() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "file_id": "file-1234",
                      "key": "files/2026/09/profile_photo/photo.jpg",
                      "upload_url": "https://storage.example/upload-url",
                      "headers": {
                        "Content-Type": "image/jpeg"
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val outcome = repository.presign(
            PresignUploadRequest(
                originalName = "my_photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024L,
                purpose = FilePurpose.PROFILE_PHOTO,
            ),
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/files/presign", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"original_name\":\"my_photo.jpg\""))
        assertTrue(body.contains("\"mime_type\":\"image/jpeg\""))
        assertTrue(body.contains("\"size_bytes\":1024"))
        assertTrue(body.contains("\"purpose\":\"profile_photo\""))

        assertTrue("Expected Success, got $outcome", outcome is PresignUploadOutcome.Success)
        val result = (outcome as PresignUploadOutcome.Success).result
        assertEquals("file-1234", result.fileId)
        assertEquals("files/2026/09/profile_photo/photo.jpg", result.key)
        assertEquals("https://storage.example/upload-url", result.uploadUrl)
        assertEquals("image/jpeg", result.headers["Content-Type"])
    }

    @Test
    fun presign_maps_401_to_Unauthorized() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"token_expired"}"""),
        )

        val outcome = repository.presign(
            PresignUploadRequest(
                originalName = "photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024L,
                purpose = FilePurpose.PROFILE_PHOTO,
            ),
        )

        assertTrue("Expected Unauthorized, got $outcome", outcome is PresignUploadOutcome.Failure.Unauthorized)
    }

    @Test
    fun presign_maps_500_to_Server() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"internal_server_error"}"""),
        )

        val outcome = repository.presign(
            PresignUploadRequest(
                originalName = "photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024L,
                purpose = FilePurpose.PROFILE_PHOTO,
            ),
        )

        assertTrue("Expected Server failure, got $outcome", outcome is PresignUploadOutcome.Failure.Server)
        assertEquals(500, (outcome as PresignUploadOutcome.Failure.Server).code)
    }

    @Test
    fun presign_maps_network_error_to_Network() = runBlocking {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
        )

        val outcome = repository.presign(
            PresignUploadRequest(
                originalName = "photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024L,
                purpose = FilePurpose.PROFILE_PHOTO,
            ),
        )

        assertTrue("Expected Network failure, got $outcome", outcome is PresignUploadOutcome.Failure.Network)
    }

    @Test
    fun confirm_posts_snake_case_payload_and_maps_response() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "id": "file-1234",
                      "url": "https://storage.example/public/photo.jpg",
                      "original_name": "photo.jpg",
                      "mime_type": "image/jpeg",
                      "type": "image"
                    }
                    """.trimIndent(),
                ),
        )

        val outcome = repository.confirm(
            fileId = "file-1234",
            request = ConfirmUploadRequest(
                key = "files/2026/09/profile_photo/photo.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024L,
            ),
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/files/file-1234/confirm", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"key\":\"files/2026/09/profile_photo/photo.jpg\""))
        assertTrue(body.contains("\"mime_type\":\"image/jpeg\""))
        assertTrue(body.contains("\"size_bytes\":1024"))

        assertTrue("Expected Success, got $outcome", outcome is ConfirmUploadOutcome.Success)
        val file = (outcome as ConfirmUploadOutcome.Success).file
        assertEquals("file-1234", file.id)
        assertEquals("photo.jpg", file.originalName)
        assertEquals("image/jpeg", file.mimeType)
    }

    @Test
    fun confirm_maps_400_to_Server() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"file_not_found"}"""),
        )

        val outcome = repository.confirm(
            fileId = "file-1234",
            request = ConfirmUploadRequest(
                key = "invalid/key",
                mimeType = "image/jpeg",
                sizeBytes = 1024L,
            ),
        )

        assertTrue("Expected Server failure, got $outcome", outcome is ConfirmUploadOutcome.Failure.Server)
        assertEquals(400, (outcome as ConfirmUploadOutcome.Failure.Server).code)
    }

    @Test
    fun uploadBytes_delegates_to_FileUploader() = runBlocking {
        val bytes = byteArrayOf(1, 2, 3)
        val headers = mapOf("Content-Type" to "image/jpeg")

        val outcome = repository.uploadBytes(
            uploadUrl = "https://storage.example/upload",
            headers = headers,
            bytes = bytes,
        )

        assertEquals(UploadBytesOutcome.Success, outcome)
        assertEquals("https://storage.example/upload", stubUploader.lastUrl)
        assertEquals(headers, stubUploader.lastHeaders)
        assertEquals(bytes, stubUploader.lastBytes)
    }

    private class RecordingFileUploader : FileUploader {
        var lastUrl: String? = null
        var lastHeaders: Map<String, String>? = null
        var lastBytes: ByteArray? = null

        override suspend fun upload(
            uploadUrl: String,
            headers: Map<String, String>,
            bytes: ByteArray,
        ): UploadBytesOutcome {
            lastUrl = uploadUrl
            lastHeaders = headers
            lastBytes = bytes
            return UploadBytesOutcome.Success
        }
    }
}
