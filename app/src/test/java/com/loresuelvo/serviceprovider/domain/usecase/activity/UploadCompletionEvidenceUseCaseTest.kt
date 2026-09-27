package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidenceReader
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.file.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UploadCompletionEvidenceUseCaseTest {
    private val image = PreparedEvidenceImage("evidence.jpg", "image/jpeg", 3, "/private/evidence.jpg")
    private val bytes = byteArrayOf(1, 2, 3)
    private val reader = object : CompletionEvidenceReader {
        var result: ByteArray? = bytes
        override suspend fun read(image: PreparedEvidenceImage): ByteArray? = result
    }
    private val files = FakeFiles()
    private val useCase = UploadCompletionEvidenceUseCase(files, reader)

    @Test fun `uploads prepared bytes and returns only matching confirmed ID`() = runTest {
        assertEquals(CompletionEvidenceUpload.Success("confirmed-1"), useCase(image))
        assertEquals(PresignUploadRequest("evidence.jpg", "image/jpeg", 3, FilePurpose.WORK_ORDER_COMPLETION_IMAGE), files.request)
        assertEquals("https://storage.example/put", files.uploadUrl)
        assertEquals(mapOf("Content-Type" to "image/jpeg"), files.uploadHeaders)
        assertArrayEquals(bytes, files.uploadBytes)
        assertEquals("confirmed-1", files.confirmId)
        assertEquals(ConfirmUploadRequest("private/key", "image/jpeg", 3), files.confirmRequest)
    }

    @Test fun `each failed stage stops before confirmation and yields no ID`() = runTest {
        files.presign = PresignUploadOutcome.Failure.Server(503, "private response")
        assertEquals(CompletionEvidenceUpload.Failure(CompletionUploadStage.PRESIGN, CompletionUploadFailure.SERVER, 503), useCase(image))
        assertEquals(0, files.uploadCalls)
        files.presign = files.successPresign()

        reader.result = null
        assertEquals(CompletionEvidenceUpload.Failure(CompletionUploadStage.LOCAL_FILE, CompletionUploadFailure.UNREADABLE), useCase(image))
        assertEquals(0, files.uploadCalls)
        reader.result = bytes

        files.upload = UploadBytesOutcome.Failure.Server(403, "storage signature")
        assertEquals(CompletionEvidenceUpload.Failure(CompletionUploadStage.TRANSFER, CompletionUploadFailure.SERVER, 403), useCase(image))
        assertEquals(0, files.confirmCalls)
        files.upload = UploadBytesOutcome.Success

        files.confirm = ConfirmUploadOutcome.Failure.Unauthorized("private response")
        assertEquals(CompletionEvidenceUpload.Failure(CompletionUploadStage.CONFIRM, CompletionUploadFailure.UNAUTHORIZED), useCase(image))
    }

    @Test fun `rejects mismatched confirmation ID`() = runTest {
        files.confirm = ConfirmUploadOutcome.Success(ConfirmedFile("other", mimeType = "image/jpeg", originalName = "evidence.jpg"))
        assertEquals(CompletionEvidenceUpload.Failure(CompletionUploadStage.CONFIRM, CompletionUploadFailure.INVALID_RESPONSE), useCase(image))
    }

    @Test(expected = CancellationException::class)
    fun `presign cancellation propagates`() = runTest {
        files.cancel = true
        useCase(image)
    }

    private class FakeFiles : FileRepository {
        var request: PresignUploadRequest? = null
        var uploadUrl: String? = null
        var uploadHeaders: Map<String, String>? = null
        var uploadBytes: ByteArray? = null
        var confirmId: String? = null
        var confirmRequest: ConfirmUploadRequest? = null
        var uploadCalls = 0
        var confirmCalls = 0
        var cancel = false
        fun successPresign() = PresignUploadOutcome.Success(PresignUploadResult("confirmed-1", "private/key", "https://storage.example/put", mapOf("Content-Type" to "image/jpeg")))
        var presign: PresignUploadOutcome = successPresign()
        var upload: UploadBytesOutcome = UploadBytesOutcome.Success
        var confirm: ConfirmUploadOutcome = ConfirmUploadOutcome.Success(ConfirmedFile("confirmed-1", mimeType = "image/jpeg", originalName = "evidence.jpg"))
        override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome {
            this.request = request
            if (cancel) throw CancellationException()
            return presign
        }
        override suspend fun uploadBytes(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray): UploadBytesOutcome {
            uploadCalls++
            this.uploadUrl = uploadUrl
            uploadHeaders = headers
            uploadBytes = bytes
            return upload
        }
        override suspend fun confirm(fileId: String, request: ConfirmUploadRequest): ConfirmUploadOutcome {
            confirmCalls++
            confirmId = fileId
            confirmRequest = request
            return confirm
        }
    }
}
