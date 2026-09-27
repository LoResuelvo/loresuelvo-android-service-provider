package com.loresuelvo.serviceprovider.domain.activity

data class PreparedEvidenceImage(
    val originalName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
)

sealed interface EvidenceImagePreparation {
    data class Ready(val image: PreparedEvidenceImage) : EvidenceImagePreparation

    sealed interface Invalid : EvidenceImagePreparation {
        data object UnsupportedFormat : Invalid
        data object ExceedsMaxSize : Invalid
        data object EmptyFile : Invalid
        data object Unreadable : Invalid
        data object CorruptContent : Invalid
    }
}

interface CompletionEvidencePreparer {
    suspend fun prepare(source: String): EvidenceImagePreparation
    suspend fun clean(image: PreparedEvidenceImage)
}
