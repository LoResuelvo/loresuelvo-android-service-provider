package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for the `GET /conversations/{id}` response — the full
 * snapshot of a single conversation including its complete ordered
 * `messages` thread.
 *
 * The detail endpoint nests the `counterpart` block under a `work`
 * discriminator (the API serves two conversation kinds: `work` and
 * `chatbot`). Provider chat only ever exercises the `work` branch
 * so the mapper reads `work.counterpart`; the fallback to the root
 * `counterpart` field keeps the DTO tolerant against a future
 * shape drift where the work wrapper is dropped.
 *
 * The backend also emits `type`, `chatbot`, and (per-message)
 * `images` keys that the client doesn't model. `ignoreUnknownKeys`
 * in the global Json config drops them from the decoded object.
 */
@Serializable
data class ConversationDetailDto(
    @SerialName("id") val id: Int,
    @SerialName("status") val status: String,
    @SerialName("work") val work: WorkDto? = null,
    @SerialName("counterpart") val counterpart: ConversationCounterpartDto? = null,
    @SerialName("messages") val messages: List<ConversationMessageDto> = emptyList(),
    @SerialName("updated_on") val updatedOn: String? = null,
) {
    /**
     * Wrapper the dev backend emits around the counterpart block on
     * the detail endpoint. Future revisions may drop the wrapper —
     * the mapper falls back to the root `counterpart` field when
     * `work` is absent.
     */
    @Serializable
    data class WorkDto(
        @SerialName("counterpart") val counterpart: ConversationCounterpartDto,
    )
}
