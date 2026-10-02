package com.loresuelvo.serviceprovider.data.media

import com.loresuelvo.serviceprovider.domain.conversation.MediaReadException
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Read at most limit + one byte; reject before retaining bytes beyond the cap. */
internal fun readBoundedMedia(stream: InputStream, limit: Long): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val remaining = limit - output.size()
        val count = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining + 1).toInt())
        if (count < 0) return output.toByteArray()
        if (count.toLong() > remaining) {
            throw MediaReadException(SendMessageOutcome.Failure.PayloadTooLarge(limit))
        }
        output.write(buffer, 0, count)
    }
}
