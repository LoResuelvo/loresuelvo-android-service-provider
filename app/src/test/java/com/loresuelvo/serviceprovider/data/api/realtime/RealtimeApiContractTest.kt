package com.loresuelvo.serviceprovider.data.api.realtime

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.data.api.AuthInterceptor
import com.loresuelvo.serviceprovider.data.api.BackendApi
import com.loresuelvo.serviceprovider.domain.realtime.ProviderEvent
import com.loresuelvo.serviceprovider.ui.realtime.RealtimeTestSessions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit

class RealtimeApiContractTest {
    @Test fun `HTTP issues authenticated ticket and socket receives event with ticket only`() = runBlocking {
        val server = MockWebServer()
        val sessions = RealtimeTestSessions()
        val json = Json { ignoreUnknownKeys = true }
        val http = OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessions)).build()
        val sockets = OkHttpClient.Builder().build()
        server.enqueue(MockResponse().setBody("""{"ticket":"one use + ticket"}""").addHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"conversation.message.created","conversation_id":42,"message":{"id":3,"sender_role":"consumer","content":"hello","created_on":"2026-10-02T12:00:00Z"}}""")
            }
        }))
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .client(http).addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(BackendApi::class.java)
        val client = OkHttpRealtimeClient(api, sockets, json, sessions, server.url("/"), true) { 100 }
        val event = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { client.events.first() } }
        val connection = launch { client.connect(sessions.getSession()!!) }
        try {
            val received = event.await().event as ProviderEvent.MessageCreated
            assertEquals("hello", received.message.content)
            val ticketRequest = withContext(Dispatchers.IO) { server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS) }!!
            val socketRequest = withContext(Dispatchers.IO) { server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS) }!!
            assertEquals("POST", ticketRequest.method)
            assertEquals("/ws-tickets", ticketRequest.path)
            assertEquals("Bearer token-a", ticketRequest.getHeader("Authorization"))
            assertEquals("/ws?ticket=one%20use%20%2B%20ticket&role=provider", socketRequest.path)
            assertNull(socketRequest.getHeader("Authorization"))
        } finally {
            connection.cancelAndJoin()
            event.cancelAndJoin()
            http.connectionPool.evictAll()
            sockets.connectionPool.evictAll()
            http.dispatcher.executorService.shutdown()
            sockets.dispatcher.executorService.shutdown()
            server.shutdown()
        }
    }
}
