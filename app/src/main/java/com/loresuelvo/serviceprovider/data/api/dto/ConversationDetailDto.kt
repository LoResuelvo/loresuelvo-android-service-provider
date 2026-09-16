package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for the `GET /conversations/{id}` response — the full
 * snapshot of a single conversation including its complete
 * ordered `messages` thread.
 *
 * Mirrors the shape of the list-summary [ConversationDto] but
 * with `messages: []` instead of `last_message` and no implicit
 * omission of empty threads (an empty list is a valid response
 * for a brand-new conversation). The mapper projects both into
 * the same domain shape via the existing `toDomain` extension.
 */
@Serializable
data class ConversationDetailDto(
    @SerialName("id") val id: Int,
    @SerialName("status") val status: String,
    @SerialName("counterpart") val counterpart: ConversationCounterpartDto,
    @SerialName("messages") val messages: List<ConversationMessageDto> = emptyList(),
    @SerialName("updated_on") val updatedOn: String? = null,
)
