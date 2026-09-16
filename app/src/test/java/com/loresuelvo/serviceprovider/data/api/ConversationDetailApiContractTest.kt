package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ConversationDetailApiContractTest {

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
    fun get_conversation_by_id_returns_full_thread() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": 42,
                  "type": "work",
                  "status": "active",
                  "work": {
                    "counterpart": {
                      "id": 20,
                      "role": "consumer",
                      "name": "Ana",
                      "surname": "Pérez",
                      "profile_photo_url": null
                    }
                  },
                  "messages": [
                    {
                      "id": 1,
                      "sender_role": "consumer",
                      "content": "Hola",
                      "images": [],
                      "created_on": "2026-05-30T14:20:00Z"
                    },
                    {
                      "id": 2,
                      "sender_role": "provider",
                      "content": "Listo",
                      "images": [],
                      "created_on": "2026-05-30T14:25:00Z"
                    }
                  ],
                  "updated_on": "2026-05-30T14:25:00Z"
                }
                """.trimIndent(),
            ),
        )

        val outcome = repository().getConversationById(42)
        val request = server.takeRequest()

        assertEquals("/conversations/42", request.path)
        assertEquals("Bearer synthetic-token", request.getHeader("Authorization"))

        val detail = (outcome as ConversationDetailOutcome.Success).detail
        assertEquals(42, detail.id)
        assertEquals("Ana", detail.counterpart.name)
        assertEquals(2, detail.messages.size)
        assertEquals(ConversationSender.Consumer, detail.messages[0].sender)
        assertEquals(ConversationSender.Provider, detail.messages[1].sender)
        assertEquals("Listo", detail.messages[1].content)
    }

    @Test
    fun get_conversation_by_id_returns_empty_messages_for_brand_new_thread() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": 42,
                  "type": "work",
                  "status": "active",
                  "work": {
                    "counterpart": {
                      "id": 20,
                      "role": "consumer",
                      "name": "Ana",
                      "surname": "Pérez"
                    }
                  },
                  "messages": [],
                  "updated_on": "2026-05-30T14:25:00Z"
                }
                """.trimIndent(),
            ),
        )

        val outcome = repository().getConversationById(42)
        val detail = (outcome as ConversationDetailOutcome.Success).detail

        assertEquals(42, detail.id)
        assertTrue(detail.messages.isEmpty())
    }

    @Test
    fun get_conversation_by_id_maps_404_to_NotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val outcome = repository().getConversationById(42)

        assertTrue(outcome is ConversationDetailOutcome.Failure.NotFound)
    }

    @Test
    fun post_message_returns_server_persisted_message() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": 99,
                  "sender_role": "provider",
                  "content": "Listo para empezar",
                  "created_on": "2026-05-30T14:30:00Z"
                }
                """.trimIndent(),
            ),
        )

        val outcome = repository().sendMessage(conversationId = 42, content = "Listo para empezar")
        val request = server.takeRequest()

        assertEquals("/conversations/42/messages", request.path)
        assertEquals("POST", request.method)
        assertEquals(
            "{\"content\":\"Listo para empezar\"}",
            request.body.readUtf8(),
        )

        val message = (outcome as SendMessageOutcome.Success).message
        assertEquals(99, message.id)
        assertEquals(ConversationSender.Provider, message.sender)
        assertEquals("Listo para empezar", message.content)
    }

    @Test
    fun post_message_maps_404_to_ConversationNotFound() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val outcome = repository().sendMessage(conversationId = 42, content = "hola")

        assertTrue(outcome is SendMessageOutcome.Failure.ConversationNotFound)
    }

    private fun repository(): ApiConversationRepository {
        return ApiConversationRepository(api())
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
