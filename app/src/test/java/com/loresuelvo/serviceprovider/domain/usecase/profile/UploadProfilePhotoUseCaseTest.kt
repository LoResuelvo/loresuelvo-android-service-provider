package com.loresuelvo.serviceprovider.domain.usecase.profile

import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.ConfirmedFile
import com.loresuelvo.serviceprovider.domain.file.FilePurpose
import com.loresuelvo.serviceprovider.domain.file.FileRepository
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.PresignUploadResult
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class UploadProfilePhotoUseCaseTest {

    private lateinit var fakeFileRepository: FakeFileRepo
    private lateinit var useCase: UploadProfilePhotoUseCase
    private lateinit var validPhoto: SelectedProfilePhoto
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        fakeFileRepository = FakeFileRepo()
        useCase = UploadProfilePhotoUseCase(fakeFileRepository)

        tempFile = File.createTempFile("profile_test", ".jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
            deleteOnExit()
        }
        validPhoto = SelectedProfilePhoto(
            originalName = "profile.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 3L,
            localPath = tempFile.absolutePath,
        )
    }

    @Test
    fun successful_pipeline_returns_confirmed_file() = runBlocking {
        val outcome = useCase(validPhoto)

        assertTrue("Expected Success, got $outcome", outcome is UploadProfilePhotoOutcome.Success)
        val file = (outcome as UploadProfilePhotoOutcome.Success).confirmedFile
        assertEquals("file-123", file.id)
        assertEquals(1, fakeFileRepository.presignCalls)
        assertEquals(1, fakeFileRepository.uploadCalls)
        assertEquals(1, fakeFileRepository.confirmCalls)
        assertEquals(FilePurpose.PROFILE_PHOTO, fakeFileRepository.lastPresignRequest?.purpose)
    }

    @Test
    fun presign_failure_short_circuits_pipeline() = runBlocking {
        fakeFileRepository.presignOutcome = PresignUploadOutcome.Failure.Server(500, "Server error")

        val outcome = useCase(validPhoto)

        assertTrue("Expected Server failure, got $outcome", outcome is UploadProfilePhotoOutcome.Failure.Server)
        assertEquals(500, (outcome as UploadProfilePhotoOutcome.Failure.Server).code)
        assertEquals(0, fakeFileRepository.uploadCalls)
        assertEquals(0, fakeFileRepository.confirmCalls)
    }

    @Test
    fun missing_local_file_short_circuits_before_upload() = runBlocking {
        val missingPhoto = validPhoto.copy(localPath = "/nonexistent/path/photo.jpg")

        val outcome = useCase(missingPhoto)

        assertTrue("Expected LocalFileError, got $outcome", outcome is UploadProfilePhotoOutcome.Failure.LocalFileError)
        assertEquals(1, fakeFileRepository.presignCalls)
        assertEquals(0, fakeFileRepository.uploadCalls)
        assertEquals(0, fakeFileRepository.confirmCalls)
    }

    @Test
    fun upload_failure_short_circuits_before_confirm() = runBlocking {
        fakeFileRepository.uploadBytesOutcome = UploadBytesOutcome.Failure.Server(403, "Forbidden")

        val outcome = useCase(validPhoto)

        assertTrue("Expected Server failure, got $outcome", outcome is UploadProfilePhotoOutcome.Failure.Server)
        assertEquals(403, (outcome as UploadProfilePhotoOutcome.Failure.Server).code)
        assertEquals(1, fakeFileRepository.presignCalls)
        assertEquals(1, fakeFileRepository.uploadCalls)
        assertEquals(0, fakeFileRepository.confirmCalls)
    }

    @Test
    fun confirm_failure_returns_failure() = runBlocking {
        fakeFileRepository.confirmOutcome = ConfirmUploadOutcome.Failure.Server(400, "Invalid key")

        val outcome = useCase(validPhoto)

        assertTrue("Expected Server failure, got $outcome", outcome is UploadProfilePhotoOutcome.Failure.Server)
        assertEquals(400, (outcome as UploadProfilePhotoOutcome.Failure.Server).code)
        assertEquals(1, fakeFileRepository.presignCalls)
        assertEquals(1, fakeFileRepository.uploadCalls)
        assertEquals(1, fakeFileRepository.confirmCalls)
    }

    @Test(expected = CancellationException::class)
    fun cancellation_rethrows_immediately(): Unit = runBlocking {
        fakeFileRepository.presignOutcomeThrow = CancellationException("Coroutines cancelled")

        useCase(validPhoto)
    }

    private class FakeFileRepo : FileRepository {
        var presignCalls = 0
        var uploadCalls = 0
        var confirmCalls = 0
        var lastPresignRequest: PresignUploadRequest? = null

        var presignOutcomeThrow: Throwable? = null
        var presignOutcome: PresignUploadOutcome = PresignUploadOutcome.Success(
            PresignUploadResult(
                fileId = "file-123",
                key = "keys/profile.jpg",
                uploadUrl = "https://storage.example/upload",
                headers = mapOf("Content-Type" to "image/jpeg"),
            ),
        )
        var uploadBytesOutcome: UploadBytesOutcome = UploadBytesOutcome.Success
        var confirmOutcome: ConfirmUploadOutcome = ConfirmUploadOutcome.Success(
            ConfirmedFile(
                id = "file-123",
                originalName = "profile.jpg",
                mimeType = "image/jpeg",
            ),
        )

        override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome {
            presignCalls++
            lastPresignRequest = request
            presignOutcomeThrow?.let { throw it }
            return presignOutcome
        }

        override suspend fun uploadBytes(
            uploadUrl: String,
            headers: Map<String, String>,
            bytes: ByteArray,
        ): UploadBytesOutcome {
            uploadCalls++
            return uploadBytesOutcome
        }

        override suspend fun confirm(
            fileId: String,
            request: ConfirmUploadRequest,
        ): ConfirmUploadOutcome {
            confirmCalls++
            return confirmOutcome
        }
    }
}
