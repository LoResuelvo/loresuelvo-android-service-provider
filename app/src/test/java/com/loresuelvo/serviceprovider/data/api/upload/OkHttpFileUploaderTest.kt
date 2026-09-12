package com.loresuelvo.serviceprovider.data.api.upload

import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OkHttpFileUploaderTest {

    private lateinit var server: MockWebServer
    private lateinit var uploader: OkHttpFileUploader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val client = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        uploader = OkHttpFileUploader(client)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun upload_2xx_returns_Success_and_sends_exact_bytes_and_no_authorization() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val outcome = uploader.upload(
            uploadUrl = server.url("/upload/profile.jpg").toString(),
            headers = mapOf(
                "Content-Type" to "image/jpeg",
                "x-amz-acl" to "public-read",
            ),
            bytes = bytes,
        )

        assertEquals(UploadBytesOutcome.Success, outcome)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals("/upload/profile.jpg", recorded.path)
        assertEquals("image/jpeg", recorded.getHeader("Content-Type"))
        assertEquals("public-read", recorded.getHeader("x-amz-acl"))
        assertEquals(4, recorded.bodySize)
        assertNull("Signed upload MUST NOT carry Authorization header", recorded.getHeader("Authorization"))
    }

    @Test
    fun upload_without_content_type_succeeds_without_content_type_header() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200))

        val bytes = byteArrayOf(0x05, 0x06)
        val outcome = uploader.upload(
            uploadUrl = server.url("/upload/file").toString(),
            headers = emptyMap(),
            bytes = bytes,
        )

        assertEquals(UploadBytesOutcome.Success, outcome)
        val recorded = server.takeRequest()
        assertEquals("PUT", recorded.method)
        assertEquals(2, recorded.bodySize)
    }

    @Test
    fun upload_403_returns_Server_failure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("SignatureDoesNotMatch"),
        )

        val outcome = uploader.upload(
            uploadUrl = server.url("/upload/profile.jpg").toString(),
            headers = mapOf("Content-Type" to "image/jpeg"),
            bytes = byteArrayOf(0x01),
        )

        val failure = outcome as? UploadBytesOutcome.Failure.Server
        assertTrue("Expected Server failure, got $outcome", failure != null)
        assertEquals(403, failure!!.code)
    }

    @Test
    fun upload_transport_drop_returns_Network_failure() = runBlocking {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST),
        )

        val outcome = uploader.upload(
            uploadUrl = server.url("/upload/profile.jpg").toString(),
            headers = mapOf("Content-Type" to "image/jpeg"),
            bytes = byteArrayOf(0x01),
        )

        assertTrue("Expected Network failure, got $outcome", outcome is UploadBytesOutcome.Failure.Network)
    }

    @Test(expected = CancellationException::class)
    fun upload_rethrows_CancellationException(): Unit = runBlocking {
        val deferred = async {
            uploader.upload(
                uploadUrl = server.url("/upload/profile.jpg").toString(),
                headers = emptyMap(),
                bytes = byteArrayOf(0x01),
            )
        }
        deferred.cancel(CancellationException("Upload cancelled"))
        deferred.await()
    }
}
