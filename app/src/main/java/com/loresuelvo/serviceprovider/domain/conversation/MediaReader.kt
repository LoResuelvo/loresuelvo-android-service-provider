package com.loresuelvo.serviceprovider.domain.conversation

import java.io.IOException

/** Platform adapter reads a bounded local attachment from an opaque URI string. */
interface MediaReader {
    suspend fun read(uri: String): MediaUpload
}

class MediaReadException(val failure: SendMessageOutcome.Failure) : IOException("Invalid local media")
