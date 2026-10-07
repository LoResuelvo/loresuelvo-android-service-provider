package com.loresuelvo.serviceprovider.notifications

import com.loresuelvo.serviceprovider.data.api.mapper.ProviderNotificationParser
import org.junit.Assert.*
import org.junit.Test

class ProviderNotificationParserTest {
    @Test fun supported_data_schema_requires_every_routing_field_and_rejects_unknown_types() {
        val parser = ProviderNotificationParser()
        val valid = payload()
        assertEquals(42, parser.parse(valid)?.conversationId)
        valid.keys.forEach { field -> assertNull("Missing $field", parser.parse(valid - field)) }
        mapOf("version" to "2", "recipient_app" to "consumer", "type" to "unknown", "destination" to "work_order",
            "resource_type" to "work_order", "resource_id" to "0", "recipient_user_id" to "auth0|subject",
            "installation_id" to "not-uuid", "binding_id" to "not-uuid", "expires_at" to "bad", "title" to " ", "body" to " ").forEach { (field, value) ->
            assertNull("Invalid $field", parser.parse(valid + (field to value)))
        }
    }

    companion object {
        fun payload(): Map<String, String> = mapOf(
            "version" to "1", "event_id" to "message:123:7", "type" to "conversation.message.created",
            "resource_type" to "conversation", "resource_id" to "42", "destination" to "conversation",
            "recipient_user_id" to "7", "recipient_app" to "provider",
            "installation_id" to "10000000-0000-4000-8000-000000000001",
            "binding_id" to "20000000-0000-4000-8000-000000000002",
            "title" to "Nuevo mensaje", "body" to "Tenés un mensaje nuevo. Abrí LoResuelvo para responder.",
            "expires_at" to "2026-10-08T12:00:00Z",
        )
    }
}
