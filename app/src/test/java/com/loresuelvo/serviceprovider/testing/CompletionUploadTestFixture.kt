package com.loresuelvo.serviceprovider.testing

import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidenceReader
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.file.*
import com.loresuelvo.serviceprovider.domain.usecase.activity.UploadCompletionEvidenceUseCase

fun unusedCompletionUploadUseCase() = UploadCompletionEvidenceUseCase(
    object : FileRepository {
        override suspend fun presign(request: PresignUploadRequest): PresignUploadOutcome = error("Upload not expected")
        override suspend fun uploadBytes(uploadUrl: String, headers: Map<String, String>, bytes: ByteArray): UploadBytesOutcome = error("Upload not expected")
        override suspend fun confirm(fileId: String, request: ConfirmUploadRequest): ConfirmUploadOutcome = error("Upload not expected")
    },
    object : CompletionEvidenceReader {
        override suspend fun read(image: PreparedEvidenceImage): ByteArray? = error("Upload not expected")
    },
)
