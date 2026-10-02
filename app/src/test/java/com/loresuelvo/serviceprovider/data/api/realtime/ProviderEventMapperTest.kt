package com.loresuelvo.serviceprovider.data.api.realtime

import com.loresuelvo.serviceprovider.data.api.mapper.decodeProviderEvent
import com.loresuelvo.serviceprovider.domain.conversation.MediaReference
import com.loresuelvo.serviceprovider.domain.realtime.ProviderEvent
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProviderEventMapperTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun message(fields: String, role: String = "consumer") =
        """{"type":"conversation.message.created","conversation_id":42,"message":{"id":3,"sender_role":"$role","content":"hello","created_on":"2026-10-02T12:00:00Z"$fields}}"""

    @Test fun `text image and audio fields map through established conversation contract`() {
        val text = json.decodeProviderEvent(message("")) as ProviderEvent.MessageCreated
        assertEquals(42, text.conversationId)
        assertEquals(1_790_942_400_000L, text.message.createdOnEpochMillis)
        val images = """, "images":[{"id":"i1","url":"https://example.test/1.jpg","original_name":"1.jpg"},{"id":"i2","url":"https://example.test/2.png","original_name":"2.png"}]"""
        val image = json.decodeProviderEvent(message(images)) as ProviderEvent.MessageCreated
        assertEquals(listOf("i1", "i2"), image.message.images.map { it.id })
        val audio = """, "audio":{"id":"a1","url":"https://example.test/voice.webm","original_name":"voice.webm","mime_type":"audio/webm","codec":"opus","duration_seconds":4}"""
        val event = json.decodeProviderEvent(message(audio)) as ProviderEvent.MessageCreated
        assertEquals(MediaReference.Audio("a1", "https://example.test/voice.webm", "audio/webm", "voice.webm", 4_000), event.message.media)
    }

    @Test fun `malformed known events and unsupported roles are ignored without breaking decoding`() {
        val invalid = listOf("not-json", "[]", message("", "admin"), message("").replace("\"id\":3", "\"id\":0"),
            message("").replace("2026-10-02T12:00:00Z", "not-a-date"),
            message(""", "images":[{"id":"i1","url":"https://example.test/white space","original_name":"photo.jpg"}]"""),
            message(""", "images":[{"id":"i1","url":"https://example.test/%wrong","original_name":"photo.jpg"}]"""),
            message(""", "audio":{"id":"a","url":"https://example.test/a","original_name":"a","mime_type":"audio/webm","duration_seconds":-1}"""),
            """{"type":"future.event","message":false}""")
        invalid.forEach { assertNull(it, json.decodeProviderEvent(it)) }
        assertNotNull(json.decodeProviderEvent(message("")))
    }

    @Test fun `notification envelope preserves its separate typed contract and optional values`() {
        val text = """{"type":"notification.created","notification":{"id":5,"user_id":7,"type":"work_order_close_to_scheduled_time","resource_type":"work_order","resource_id":9,"read_at":null,"created_at":"2026-10-02T12:00:00Z"}}"""
        val notification = (json.decodeProviderEvent(text) as ProviderEvent.NotificationCreated).notification
        assertEquals(5, notification.id)
        assertEquals(7, notification.userId)
        assertEquals(9, notification.resourceId)
        assertNull(notification.readOnEpochMillis)
        assertNull(notification.estimatedDurationMinutes)
        val populated = text.replace("\"read_at\":null", "\"read_at\":\"2026-10-02T12:01:00Z\",\"estimated_duration_minutes\":30")
        val next = (json.decodeProviderEvent(populated) as ProviderEvent.NotificationCreated).notification
        assertEquals(30, next.estimatedDurationMinutes)
        assertEquals(60_000L, next.readOnEpochMillis!! - next.createdOnEpochMillis)
    }
}
