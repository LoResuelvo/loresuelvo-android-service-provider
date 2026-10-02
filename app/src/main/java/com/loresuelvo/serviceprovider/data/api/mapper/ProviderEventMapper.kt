package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.MessageCreatedDto
import com.loresuelvo.serviceprovider.data.api.dto.NotificationCreatedDto
import com.loresuelvo.serviceprovider.domain.realtime.ProviderEvent
import com.loresuelvo.serviceprovider.domain.realtime.ProviderNotification
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun Json.decodeProviderEvent(text: String): ProviderEvent? = try {
    val element = parseToJsonElement(text)
    when (element.jsonObject["type"]?.jsonPrimitive?.content) {
        "conversation.message.created" -> decodeFromJsonElement(MessageCreatedDto.serializer(), element).let {
            require(it.conversationId > 0)
            val message = it.message
            message.images.orEmpty().forEach { image ->
                require(image.id.isNotBlank() && image.originalName.isNotBlank())
                requireMediaUrl(image.url)
                require(image.mimeType in setOf("image/jpeg", "image/png", "image/webp"))
            }
            message.audio?.let { audio ->
                require(audio.id.isNotBlank() && audio.originalName.isNotBlank())
                requireMediaUrl(audio.url)
                require(audio.mimeType == "audio/webm" && audio.durationSeconds > 0)
            }
            require(message.audio == null || message.images.isNullOrEmpty())
            require(message.content.isNotBlank() || message.audio != null || !message.images.isNullOrEmpty())
            ProviderEvent.MessageCreated(it.conversationId, message.toDomain())
        }
        "notification.created" -> decodeFromJsonElement(NotificationCreatedDto.serializer(), element).notification.let {
            require(it.id > 0 && it.userId > 0 && it.resourceId > 0)
            require(it.type.isNotBlank() && it.resourceType.isNotBlank())
            require(it.estimatedDurationMinutes == null || it.estimatedDurationMinutes >= 0)
            ProviderEvent.NotificationCreated(ProviderNotification(it.id, it.userId, it.type, it.resourceType,
                it.resourceId, it.readOn?.toEpochMillis(), it.createdOn.toEpochMillis(), it.estimatedDurationMinutes))
        }
        else -> null
    }
} catch (_: java.net.URISyntaxException) {
    null
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}

private fun requireMediaUrl(url: String) {
    val uri = java.net.URI(url)
    require(uri.scheme in setOf("https", "http") && !uri.host.isNullOrBlank() && uri.userInfo == null)
}
