package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ConversationApiContractTest {

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
    fun requests_provider_conversations_with_bearer_and_nested_summary() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [{
                  "id": 7,
                  "status": "pending",
                  "counterpart": {
                    "id": 20,
                    "role": "consumer",
                    "name": "Ana",
                    "surname": "Pérez",
                    "profile_photo_url": "https://example.test/ana.jpg"
                  },
                  "last_message": {
                    "id": 9,
                    "sender_role": "consumer",
                    "content": "Hola",
                    "created_on": "2026-05-30T14:20:00Z"
                  },
                  "updated_on": "2026-05-30T14:20:00Z"
                }]
                """.trimIndent(),
            ),
        )

        val conversations = api().getConversations()
        val request = server.takeRequest()

        assertEquals("/conversations", request.path)
        assertEquals("Bearer synthetic-token", request.getHeader("Authorization"))
        assertEquals(7, conversations.single().id)
        assertEquals("Ana", conversations.single().counterpart.name)
        assertNotNull(conversations.single().lastMessage)
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

    private class SessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(
            AuthSession(User("auth0|provider", "provider@example.com"), "synthetic-token"),
        )
        override val sessionFlow: StateFlow<AuthSession?> = state
        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() { state.value = null }
    }
}
