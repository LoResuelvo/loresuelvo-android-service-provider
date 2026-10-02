package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WebSocketTicketDto(val ticket: String)

@Serializable
internal data class MessageCreatedDto(
    @SerialName("conversation_id") val conversationId: Int,
    val message: ConversationMessageDto,
)

@Serializable
internal data class NotificationCreatedDto(val notification: ProviderNotificationDto)

@Serializable
internal data class ProviderNotificationDto(
    val id: Int,
    @SerialName("user_id") val userId: Int,
    val type: String,
    @SerialName("resource_type") val resourceType: String,
    @SerialName("resource_id") val resourceId: Int,
    @SerialName("read_at") val readOn: String? = null,
    @SerialName("created_at") val createdOn: String,
    @SerialName("estimated_duration_minutes") val estimatedDurationMinutes: Int? = null,
)
