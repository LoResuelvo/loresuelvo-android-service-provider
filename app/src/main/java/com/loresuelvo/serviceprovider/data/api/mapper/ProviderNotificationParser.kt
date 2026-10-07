package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.domain.notifications.ProviderNotification
import java.time.Instant

/** Strict data-only version-1 boundary; unknown schemas never become notifications. */
class ProviderNotificationParser {
    fun parse(data: Map<String, String>): ProviderNotification? {
        if (data["version"] != "1" || data["recipient_app"] != "provider" ||
            data["type"] != "conversation.message.created" || data["destination"] != "conversation" ||
            data["resource_type"] != "conversation") return null
        val conversationId = data["resource_id"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val recipient = data["recipient_user_id"]?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val installation = data["installation_id"]?.takeIf(::isUuid) ?: return null
        val binding = data["binding_id"]?.takeIf(::isUuid) ?: return null
        val event = data["event_id"]?.takeIf { it.length <= 256 && it.matches(Regex("message:[1-9][0-9]*:$recipient")) } ?: return null
        val title = data["title"]?.takeIf { it.isNotBlank() && it.length <= 200 } ?: return null
        val body = data["body"]?.takeIf { it.isNotBlank() && it.length <= 1_000 } ?: return null
        val expires = try { Instant.parse(data["expires_at"]).toEpochMilli() } catch (_: RuntimeException) { return null }
        return ProviderNotification(event, conversationId, recipient, installation, binding, title, body, expires)
    }

    private fun isUuid(value: String): Boolean = value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
}
