package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for `POST /conversations/{id}/messages`. US-A only
 * carries the `content` field (text-only chat); future media USes
 * (B / C / D) extend this DTO with `image_file_ids`, `audio_file_id`,
 * `video_file_id` in the same way the consumer does. Keeping it as
 * a single field for now avoids speculative nullable fields that
 * could leak through mappers.
 */
@Serializable
data class SendMessageRequestDto(
    @SerialName("content") val content: String,
)
