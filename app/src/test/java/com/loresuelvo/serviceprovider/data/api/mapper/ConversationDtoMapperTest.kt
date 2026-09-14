package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.ConversationCounterpartDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationMessageDto
import com.loresuelvo.serviceprovider.data.api.dto.MessageAudioDto
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessageKind
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationDtoMapperTest {

    @Test
    fun maps_provider_summary_fields_and_preserves_api_order() {
        val mapped = listOf(
            conversation(id = 2, status = "active"),
            conversation(id = 1, status = "pending"),
        ).map { it.toDomain() }

        assertEquals(listOf(2, 1), mapped.map { it.id })
        assertEquals("Ana Pérez", "${mapped.first().counterpart.name} ${mapped.first().counterpart.surname}")
        assertEquals(ConversationStatus.Active, mapped.first().status)
    }

    @Test
    fun maps_nullable_last_message_and_media_preview_kind() {
        val withoutMessage = conversation(id = 1, lastMessage = null).toDomain()
        val withAudio = conversation(
            id = 2,
            lastMessage = ConversationMessageDto(
                id = 3,
                senderRole = "consumer",
                content = "",
                createdOn = "2026-05-30T14:20:00Z",
                audio = MessageAudioDto(),
            ),
        ).toDomain()

        assertEquals(null, withoutMessage.lastMessage)
        assertEquals(ConversationMessageKind.Audio, withAudio.lastMessage?.kind)
    }

    @Test
    fun accepts_fractional_offset_timestamps_and_unknown_statuses() {
        val mapped = conversation(
            id = 4,
            status = "future_status",
            updatedOn = "2026-05-30T14:20:00.123456-03:00",
        ).toDomain()

        assertTrue(mapped.status is ConversationStatus.Unsupported)
        assertEquals(
            Instant.parse("2026-05-30T17:20:00.123Z").toEpochMilli(),
            mapped.updatedOnEpochMillis,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_a_non_consumer_counterpart() {
        conversation(id = 1, counterpartRole = "provider").toDomain()
    }

    private fun conversation(
        id: Int,
        status: String = "pending",
        counterpartRole: String = "consumer",
        updatedOn: String = "2026-05-30T14:20:00Z",
        lastMessage: ConversationMessageDto? = ConversationMessageDto(
            id = 10,
            senderRole = "consumer",
            content = "Hola",
            createdOn = "2026-05-30T14:20:00Z",
        ),
    ) = ConversationDto(
        id = id,
        status = status,
        counterpart = ConversationCounterpartDto(
            id = 20,
            role = counterpartRole,
            name = "Ana",
            surname = "Pérez",
            profilePhotoUrl = "https://example.test/ana.jpg",
        ),
        lastMessage = lastMessage,
        updatedOn = updatedOn,
    )
}
