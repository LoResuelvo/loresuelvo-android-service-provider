package com.loresuelvo.serviceprovider.data.media

import com.loresuelvo.serviceprovider.domain.conversation.MAX_IMAGE_BYTES
import com.loresuelvo.serviceprovider.domain.conversation.MediaReadException
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedMediaInputTest {
    @Test
    fun exact_limit_is_accepted_and_one_extra_byte_is_rejected_before_reading_the_rest() {
        val exact = ByteArray(MAX_IMAGE_BYTES.toInt())
        assertEquals(exact.size, readBoundedMedia(exact.inputStream(), MAX_IMAGE_BYTES).size)
        var consumed = 0L
        val endless = object : InputStream() {
            override fun read(): Int { consumed++; return 1 }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer.fill(1, offset, offset + length)
                consumed += length
                return length
            }
        }
        try {
            readBoundedMedia(endless, MAX_IMAGE_BYTES)
            error("Oversized input must fail")
        } catch (e: MediaReadException) {
            assertTrue(e.failure is SendMessageOutcome.Failure.PayloadTooLarge)
            assertEquals(MAX_IMAGE_BYTES + 1, consumed)
        }
    }
}
