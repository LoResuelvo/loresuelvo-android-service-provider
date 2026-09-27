package com.loresuelvo.serviceprovider.data.media

import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidencePreparer
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.profile.PhotoValidationOutcome
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import com.loresuelvo.serviceprovider.domain.profile.ProfilePhotoLimits
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class AndroidCompletionEvidencePreparer @Inject constructor(
    private val imagePreparer: AndroidProfilePhotoPreparer,
    @ApplicationContext private val context: Context,
) : CompletionEvidencePreparer {
    override suspend fun isAvailable(image: PreparedEvidenceImage): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(image.localPath).canonicalFile
            file.parentFile == File(context.cacheDir, "completion_evidence").canonicalFile &&
                file.isFile && file.canRead() &&
                image.sizeBytes in 1..ProfilePhotoLimits.MAX_BYTES && file.length() == image.sizeBytes
        }.getOrDefault(false)
    }
    override suspend fun prepare(source: String): EvidenceImagePreparation =
        when (val outcome = imagePreparer.prepareImage(source, "completion_evidence", "evidence_image")) {
            is PhotoValidationOutcome.Valid -> EvidenceImagePreparation.Ready(
                PreparedEvidenceImage(
                    originalName = outcome.photo.originalName,
                    mimeType = outcome.photo.mimeType,
                    sizeBytes = outcome.photo.sizeBytes,
                    localPath = outcome.photo.localPath,
                ),
            )
            PhotoValidationOutcome.Invalid.UnsupportedFormat -> EvidenceImagePreparation.Invalid.UnsupportedFormat
            PhotoValidationOutcome.Invalid.ExceedsMaxSize -> EvidenceImagePreparation.Invalid.ExceedsMaxSize
            PhotoValidationOutcome.Invalid.EmptyFile -> EvidenceImagePreparation.Invalid.EmptyFile
            PhotoValidationOutcome.Invalid.Unreadable -> EvidenceImagePreparation.Invalid.Unreadable
            PhotoValidationOutcome.Invalid.CorruptContent -> EvidenceImagePreparation.Invalid.CorruptContent
        }

    override suspend fun clean(image: PreparedEvidenceImage) {
        imagePreparer.cleanPhoto(
            SelectedProfilePhoto(image.originalName, image.mimeType, image.sizeBytes, image.localPath),
        )
    }
}
