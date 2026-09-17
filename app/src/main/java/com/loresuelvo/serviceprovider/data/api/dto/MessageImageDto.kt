package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for a single image attached to a conversation
 * message (US-B). The backend emits this both on the conversation
 * detail endpoint (`GET /conversations/{id}`) and on the
 * conversation list summary (`last_message.images`).
 *
 * The provider chat keeps one image per bubble for US-B — the
 * mapper extracts the first `images[]` entry into a single
 * [com.loresuelvo.serviceprovider.domain.conversation.MediaReference.Image]
 * on the message. Future revisions that allow multi-image
 * bubbles would extend the domain type to `List<MediaReference>`
 * — keeping the mapper line for line with the wire avoids
 * scattering `if (first)` ceremony across the chat surface.
 */
@Serializable
data class MessageImageDto(
    @SerialName("id") val id: String,
    @SerialName("url") val url: String,
    @SerialName("original_name") val originalName: String,
    @SerialName("mime_type") val mimeType: String = "image/jpeg",
    @SerialName("width") val width: Int = 0,
    @SerialName("height") val height: Int = 0,
)
