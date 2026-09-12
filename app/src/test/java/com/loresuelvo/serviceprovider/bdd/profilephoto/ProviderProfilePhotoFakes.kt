package com.loresuelvo.serviceprovider.bdd.profilephoto

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.category.CategoriesOutcome
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.category.CategoryRepository
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.ConfirmUploadRequest
import com.loresuelvo.serviceprovider.domain.file.ConfirmedFile
import com.loresuelvo.serviceprovider.domain.file.FileRepository
import com.loresuelvo.serviceprovider.domain.file.PresignUploadOutcome
import com.loresuelvo.serviceprovider.domain.file.PresignUploadRequest
import com.loresuelvo.serviceprovider.domain.file.PresignUploadResult
import com.loresuelvo.serviceprovider.domain.file.UploadBytesOutcome
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoPreparer
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import com.loresuelvo.serviceprovider.domain.provider.ProviderRegistrationCommand
import com.loresuelvo.serviceprovider.domain.provider.ProviderRepository
import com.loresuelvo.serviceprovider.domain.provider.RegistrationOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeCategoryRepository : CategoryRepository {
    var outcome: CategoriesOutcome = CategoriesOutcome.Success(
        listOf(
            Category(1, "Plomería"),
            Category(2, "Electricidad"),
            Category(3, "Pintura"),
        ),
    )

    override suspend fun getCategories(): CategoriesOutcome = outcome
}

class FakeProviderRepository : ProviderRepository {
    var outcome: RegistrationOutcome = RegistrationOutcome.Success(providerId = 1)
    var registerCalls: Int = 0
        private set
    var lastCommand: ProviderRegistrationCommand? = null
        private set
    var registerGate: CompletableDeferred<Unit>? = null

    override suspend fun register(command: ProviderRegistrationCommand): RegistrationOutcome {
        registerCalls += 1
        lastCommand = command
        registerGate?.await()
        return outcome
    }
}

class FakeAuthSessionStore : AuthSessionStore {
    private val _session = MutableStateFlow<AuthSession?>(null)
    override val sessionFlow: StateFlow<AuthSession?> = _session

    override fun getSession(): AuthSession? = _session.value

    override fun saveSession(session: AuthSession) {
        _session.value = session
    }

    override fun clearSession() {
        _session.value = null
    }
}

class FakeProfilePhotoPreparer : ProfilePhotoPreparer {
    var outcome: PhotoValidationOutcome = PhotoValidationOutcome.Valid(
        SelectedProfilePhoto(
            originalName = "valid_profile.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1024L,
            localPath = "/cache/valid_profile.jpg",
        ),
    )

    override suspend fun preparePhoto(source: String): PhotoValidationOutcome = outcome

    override suspend fun cleanPhoto(photo: SelectedProfilePhoto) {}
}

class FakeFileRepository : FileRepository {
    var presignOutcome: PresignUploadOutcome = PresignUploadOutcome.Success(
        PresignUploadResult(
            fileId = "file_uploaded_123",
            key = "profile/photo.jpg",
            uploadUrl = "https://storage.example/upload",
            headers = mapOf("Content-Type" to "image/jpeg"),
        ),
    )
    var uploadBytesOutcome: UploadBytesOutcome = UploadBytesOutcome.Success
    var confirmOutcome: ConfirmUploadOutcome = ConfirmUploadOutcome.Success(
        ConfirmedFile(
            id = "file_uploaded_123",
            originalName = "profile.jpg",
            mimeType = "image/jpeg",
        ),
    )

    var presignCalls: Int = 0
        private set
    var uploadCalls: Int = 0
        private set
    var confirmCalls: Int = 0
        private set

    var presignGate: CompletableDeferred<Unit>? = null
    var uploadGate: CompletableDeferred<Unit>? = null
    var confirmGate: CompletableDeferred<Unit>? = null

    override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome {
        presignCalls += 1
        presignGate?.await()
        return presignOutcome
    }

    override suspend fun uploadBytes(
        uploadUrl: String,
        headers: Map<String, String>,
        bytes: ByteArray,
    ): UploadBytesOutcome {
        uploadCalls += 1
        uploadGate?.await()
        return uploadBytesOutcome
    }

    override suspend fun confirm(
        fileId: String,
        request: ConfirmUploadRequest,
    ): ConfirmUploadOutcome {
        confirmCalls += 1
        confirmGate?.await()
        return confirmOutcome
    }

    fun resetDefaults() {
        presignOutcome = PresignUploadOutcome.Success(
            PresignUploadResult(
                fileId = "file_uploaded_123",
                key = "profile/photo.jpg",
                uploadUrl = "https://storage.example/upload",
                headers = mapOf("Content-Type" to "image/jpeg"),
            ),
        )
        uploadBytesOutcome = UploadBytesOutcome.Success
        confirmOutcome = ConfirmUploadOutcome.Success(
            ConfirmedFile(
                id = "file_uploaded_123",
                originalName = "profile.jpg",
                mimeType = "image/jpeg",
            ),
        )
        presignGate = null
        uploadGate = null
        confirmGate = null
    }
}
