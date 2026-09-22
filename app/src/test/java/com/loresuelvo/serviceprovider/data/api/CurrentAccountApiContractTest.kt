package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.CurrentAccountDto
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

class CurrentAccountApiContractTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun requests_authenticated_me_and_decodes_provider_fields() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "id": 20,
                      "name": "Juan",
                      "surname": "Gómez",
                      "email": "juan@example.com",
                      "role": "provider",
                      "calendar_connection_status": "connected",
                      "category": {"id": 1, "name": "Plomería"},
                      "profile_photo": {
                        "original_name": "profile.jpg",
                        "url": "https://cdn.example/profile.jpg"
                      },
                      "identity_verification_status": "unverified",
                      "identity_verified_on": null
                    }
                    """.trimIndent(),
                ),
        )

        val dto = api().getCurrentAccount()
        val request = server.takeRequest()

        assertEquals("/me", request.path)
        assertEquals("Bearer synthetic-token", request.getHeader("Authorization"))
        assertProvider(dto)
        assertEquals("unverified", dto.identityVerificationStatus)
        assertEquals(null, dto.identityVerifiedOn)
    }

    @Test
    fun decodes_consumer_without_provider_category_or_photo() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "id": 10,
                      "name": "Ana",
                      "surname": "Pérez",
                      "email": "ana@example.com",
                      "role": "consumer",
                      "calendar_connection_status": "disconnected",
                      "address": {"street": "Mitre", "street_number": "10"}
                    }
                    """.trimIndent(),
                ),
        )

        val dto = api().getCurrentAccount()

        assertEquals("consumer", dto.role)
        assertEquals(null, dto.category)
        assertEquals(null, dto.profilePhoto)
        assertEquals(null, dto.identityVerificationStatus)
        assertEquals(null, dto.identityVerifiedOn)
    }

    @Test
    fun decodes_approved_identity_fields_from_their_wire_names() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": 20,
                  "name": "Juan",
                  "surname": "Gómez",
                  "email": "juan@example.com",
                  "role": "provider",
                  "category": {"id": 1, "name": "Plomería"},
                  "identity_verification_status": "approved",
                  "identity_verified_on": "2026-01-15T12:34:56.123Z"
                }
                """.trimIndent(),
            ),
        )

        val dto = api().getCurrentAccount()

        assertEquals("approved", dto.identityVerificationStatus)
        assertEquals("2026-01-15T12:34:56.123Z", dto.identityVerifiedOn)
    }

    private fun api(): BackendApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(
            OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(SessionStore()))
                .build(),
        )
        .addConverterFactory(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }.asConverterFactory("application/json".toMediaType()),
        )
        .build()
        .create(BackendApi::class.java)

    private fun assertProvider(dto: CurrentAccountDto) {
        assertEquals("provider", dto.role)
        assertEquals(1, dto.category?.id)
        assertEquals("Plomería", dto.category?.name)
        assertNotNull(dto.profilePhoto)
        assertEquals("https://cdn.example/profile.jpg", dto.profilePhoto?.url)
    }

    private class SessionStore : AuthSessionStore {
        private val session = AuthSession(
            user = User("auth0|provider", "provider@example.com"),
            accessToken = "synthetic-token",
        )
        private val state = MutableStateFlow<AuthSession?>(session)
        override val sessionFlow: StateFlow<AuthSession?> = state
        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() { state.value = null }
    }
}
