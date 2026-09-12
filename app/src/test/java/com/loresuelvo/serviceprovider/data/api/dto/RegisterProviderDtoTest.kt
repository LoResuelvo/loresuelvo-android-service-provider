package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterProviderDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `serializes RegisterProviderRequestDto with snake_case fields`() {
        val request = RegisterProviderRequestDto(
            email = "provider@example.com",
            name = "Carlos",
            surname = "García",
            categoryId = 5,
            coverageZoneIds = listOf(1, 2),
            profilePhotoFileId = "photo-uuid-123",
        )

        val serialized = json.encodeToString(request)

        assertTrue(serialized.contains("\"email\":\"provider@example.com\""))
        assertTrue(serialized.contains("\"name\":\"Carlos\""))
        assertTrue(serialized.contains("\"surname\":\"García\""))
        assertTrue(serialized.contains("\"category_id\":5"))
        assertTrue(serialized.contains("\"coverage_zone_ids\":[1,2]"))
        assertTrue(serialized.contains("\"profile_photo_file_id\":\"photo-uuid-123\""))
    }

    @Test
    fun `deserializes ProviderSummaryDto correctly`() {
        val payload = """
            {
                "id": 101,
                "name": "Carlos",
                "surname": "García",
                "category_name": "Plomería",
                "profile_photo_url": "https://cdn.example.com/photo.jpg"
            }
        """.trimIndent()

        val parsed = json.decodeFromString<ProviderSummaryDto>(payload)

        assertEquals(101, parsed.id)
        assertEquals("Carlos", parsed.name)
        assertEquals("García", parsed.surname)
        assertEquals("Plomería", parsed.categoryName)
        assertEquals("https://cdn.example.com/photo.jpg", parsed.profilePhotoUrl)
    }
}
