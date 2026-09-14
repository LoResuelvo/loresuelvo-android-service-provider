package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationDto(
    @SerialName("id") val id: Int,
    @SerialName("status") val status: String,
    @SerialName("counterpart") val counterpart: ConversationCounterpartDto,
    @SerialName("last_message") val lastMessage: ConversationMessageDto? = null,
    @SerialName("updated_on") val updatedOn: String? = null,
)

@Serializable
data class ConversationCounterpartDto(
    @SerialName("id") val id: Int,
    @SerialName("role") val role: String? = null,
    @SerialName("name") val name: String,
    @SerialName("surname") val surname: String,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("profile_photo_url") val profilePhotoUrl: String? = null,
)

@Serializable
data class ConversationMessageDto(
    @SerialName("id") val id: Int,
    @SerialName("sender_role") val senderRole: String,
    @SerialName("content") val content: String,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("audio") val audio: MessageAudioDto? = null,
    @SerialName("video") val video: MessageVideoDto? = null,
)

@Serializable
data class MessageAudioDto(
    @SerialName("id") val id: String = "",
    @SerialName("url") val url: String = "",
    @SerialName("original_name") val originalName: String = "",
    @SerialName("mime_type") val mimeType: String = "audio/webm",
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
)

@Serializable
data class MessageVideoDto(
    @SerialName("id") val id: String = "",
    @SerialName("url") val url: String = "",
    @SerialName("original_name") val originalName: String = "",
    @SerialName("mime_type") val mimeType: String = "video/mp4",
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
)
