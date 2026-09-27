package com.loresuelvo.serviceprovider.domain.activity

/** Reads only a bounded, privately prepared completion image. */
interface CompletionEvidenceReader {
    suspend fun read(image: PreparedEvidenceImage): ByteArray?
}
