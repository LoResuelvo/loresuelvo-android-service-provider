package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /conversations/{id}/messages`.
 *
 *  - `content` carries the text portion of the message. May be
 *    empty when the message is media-only (an image-only bubble
 *    posts `content = ""` — the backend treats that as a valid
 *    bubble).
 *  - `image_file_ids` is `List<String>?` of confirmed file UUIDs
 *    uploaded via the presign / upload / confirm flow
 *    (`POST /files/presign` → `PUT uploadUrl` → `POST
 *    /files/{fileID}/confirm`). The backend enforces a max of 5
 *    per bubble.
 *  - `audio_file_id` is a single confirmed audio UUID. Reserved
 *    for US-C — kept nullable here so the data type stays in
 *    sync with the backend without a wire bump when audio
 *    lands.
 *
 * All attachment fields are nullable so the existing
 * text-only call sites keep compiling unchanged.
 */
@Serializable
data class SendMessageRequestDto(
    @SerialName("content") val content: String,
    @SerialName("image_file_ids") val imageFileIds: List<String>? = null,
    @SerialName("audio_file_id") val audioFileId: String? = null,
)
