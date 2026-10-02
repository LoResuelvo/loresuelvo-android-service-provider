package com.loresuelvo.serviceprovider.data.api.realtime

import com.loresuelvo.serviceprovider.data.api.BackendApi
import com.loresuelvo.serviceprovider.data.api.dto.WebSocketTicketDto
import com.loresuelvo.serviceprovider.domain.realtime.RealtimeState.Connection
import com.loresuelvo.serviceprovider.ui.realtime.RealtimeTestSessions
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import retrofit2.HttpException

@OptIn(ExperimentalCoroutinesApi::class)
class OkHttpRealtimeClientTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `fresh single use ticket retries expired handshake without bearer credentials`() = runTest {
        val sessions = RealtimeTestSessions()
        val api = mockk<BackendApi>()
        coEvery { api.createWebSocketTicket(sessions.getSession()!!) } returnsMany listOf(WebSocketTicketDto("first ticket"), WebSocketTicketDto("second ticket"))
        val sockets = ControlledSockets()
        val client = client(api, sockets, sessions)
        val job = backgroundScope.launch { client.connect(sessions.getSession()!!) }
        runCurrent()
        assertEquals("/ws?ticket=first%20ticket&role=provider", sockets.requests.single().url.encodedPath + "?" + sockets.requests.single().url.encodedQuery)
        assertNull(sockets.requests.single().header("Authorization"))
        sockets.fail(0, 401)
        runCurrent()
        assertEquals(Connection.Retrying, client.state.value.connection)
        assertNotNull(sessions.getSession())
        advanceTimeBy(100)
        runCurrent()
        assertEquals("second ticket", sockets.requests.last().url.queryParameter("ticket"))
        job.cancelAndJoin()
        assertTrue(sockets.sockets.all { it.cancelled })
    }

    @Test fun `ticket unauthorized clears only matching session and forbidden does not retry`() = runTest {
        for (code in listOf(401, 403)) {
            val sessions = RealtimeTestSessions()
            val session = sessions.getSession()!!
            val api = mockk<BackendApi>()
            coEvery { api.createWebSocketTicket(session) } throws httpFailure(code)
            val sockets = ControlledSockets()
            val client = client(api, sockets, sessions)
            val job = backgroundScope.launch { client.connect(session) }
            runCurrent()
            assertEquals(if (code == 401) Connection.Unauthorized else Connection.Unavailable, client.state.value.connection)
            assertEquals(code != 401, sessions.getSession() != null)
            advanceTimeBy(10_000)
            runCurrent()
            coVerify(exactly = 1) { api.createWebSocketTicket(session) }
            assertTrue(sockets.requests.isEmpty())
            job.cancelAndJoin()
        }
    }

    @Test fun `transient ticket timeout and rate limit keep retrying until cancelled`() = runTest {
        val sessions = RealtimeTestSessions()
        val session = sessions.getSession()!!
        val api = mockk<BackendApi>()
        var calls = 0
        coEvery { api.createWebSocketTicket(session) } answers {
            throw when (calls++) { 0 -> httpFailure(408); 1 -> httpFailure(429); else -> IOException("offline") }
        }
        val client = client(api, ControlledSockets(), sessions)
        val job = backgroundScope.launch { client.connect(session) }
        runCurrent()
        repeat(5) { advanceTimeBy(100); runCurrent() }
        assertEquals(Connection.Retrying, client.state.value.connection)
        coVerify(exactly = 6) { api.createWebSocketTicket(session) }
        repeat(3) { advanceTimeBy(100); runCurrent() }
        coVerify(exactly = 9) { api.createWebSocketTicket(session) }
        job.cancelAndJoin()
        advanceTimeBy(10_000)
        runCurrent()
        coVerify(exactly = 9) { api.createWebSocketTicket(session) }
    }

    @Test fun `successful connections reset retry budget and cancellation prevents further tickets`() = runTest {
        val sessions = RealtimeTestSessions()
        val api = mockk<BackendApi>()
        coEvery { api.createWebSocketTicket(sessions.getSession()!!) } returns WebSocketTicketDto("ticket")
        val sockets = ControlledSockets()
        val client = client(api, sockets, sessions)
        val job = backgroundScope.launch { client.connect(sessions.getSession()!!) }
        runCurrent()
        repeat(8) { index ->
            sockets.open(index)
            runCurrent()
            assertEquals(Connection.Connected, client.state.value.connection)
            sockets.fail(index, 0)
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
        }
        assertEquals(9, sockets.requests.size)
        job.cancelAndJoin()
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(9, sockets.requests.size)
        assertEquals(Connection.Stopped, client.state.value.connection)
    }

    @Test fun `retry delay advances to cap then resets after a successful upgrade`() = runTest {
        val sessions = RealtimeTestSessions()
        val api = mockk<BackendApi>()
        coEvery { api.createWebSocketTicket(sessions.getSession()!!) } returns WebSocketTicketDto("ticket")
        val sockets = ControlledSockets()
        val attempts = mutableListOf<Int>()
        val client = OkHttpRealtimeClient(api, sockets, json, sessions, "https://example.test/".toHttpUrl(), false) {
            attempts += it
            100
        }
        val job = backgroundScope.launch { client.connect(sessions.getSession()!!) }
        runCurrent()
        repeat(7) { index ->
            sockets.fail(index, 500)
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
        }
        assertEquals(listOf(0, 1, 2, 3, 4, 4, 4), attempts)
        sockets.open(7)
        runCurrent()
        sockets.fail(7, 0)
        runCurrent()
        assertEquals(0, attempts.last())
        job.cancelAndJoin()
    }

    @Test fun `concurrent replacement cancels previous socket and discards stale callbacks`() = runTest {
        val sessions = RealtimeTestSessions()
        val api = mockk<BackendApi>()
        coEvery { api.createWebSocketTicket(sessions.getSession()!!) } returns WebSocketTicketDto("ticket")
        val sockets = ControlledSockets()
        val client = client(api, sockets, sessions)
        val first = backgroundScope.launch { client.connect(sessions.getSession()!!) }
        runCurrent()
        val second = backgroundScope.launch { client.connect(sessions.getSession()!!) }
        runCurrent()
        assertTrue(first.isCancelled)
        assertTrue(sockets.sockets.first().cancelled)
        sockets.open(1)
        runCurrent()
        sockets.fail(0, 403)
        sockets.message(0, messageJson)
        runCurrent()
        assertEquals(Connection.Connected, client.state.value.connection)
        val event = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) { client.events.first() }
        sockets.message(1, messageJson)
        runCurrent()
        assertEquals(sessions.getSession(), event.await().session)
        second.cancelAndJoin()
    }

    @Test fun `overflow cancels socket and reconnects instead of silently dropping frames`() = runTest {
        val sessions = RealtimeTestSessions()
        val api = mockk<BackendApi>()
        coEvery { api.createWebSocketTicket(sessions.getSession()!!) } returns WebSocketTicketDto("ticket")
        val sockets = ControlledSockets()
        val client = client(api, sockets, sessions)
        val job = backgroundScope.launch { client.connect(sessions.getSession()!!) }
        runCurrent()
        sockets.open(0)
        runCurrent()
        repeat(66) { sockets.message(0, messageJson) }
        assertTrue(sockets.sockets.first().cancelled)
        runCurrent()
        assertEquals(Connection.Retrying, client.state.value.connection)
        advanceTimeBy(100)
        runCurrent()
        sockets.open(1)
        runCurrent()
        assertEquals(Connection.Connected, client.state.value.connection)
        assertEquals(2, sockets.requests.size)
        job.cancelAndJoin()
    }

    @Test fun `stale ticket failure cannot clear a replacement session`() = runTest {
        val sessions = RealtimeTestSessions()
        val old = sessions.getSession()!!
        val gate = CompletableDeferred<Unit>()
        val delegate = mockk<BackendApi>()
        val api = object : BackendApi by delegate {
            override suspend fun createWebSocketTicket(session: com.loresuelvo.serviceprovider.domain.auth.AuthSession): WebSocketTicketDto {
                withContext(NonCancellable) { gate.await() }
                throw httpFailure(401)
            }
        }
        val sockets = ControlledSockets()
        val client = client(api, sockets, sessions)
        val job = backgroundScope.launch { client.connect(old) }
        runCurrent()
        val replacement = old.copy(accessToken = "replacement")
        sessions.saveSession(replacement)
        gate.complete(Unit)
        runCurrent()
        assertEquals(replacement, sessions.getSession())
        assertTrue(sockets.requests.isEmpty())
        job.cancelAndJoin()
    }

    @Test fun `cleartext outside Dev fails before obtaining a ticket`() = runTest {
        val sessions = RealtimeTestSessions()
        val api = mockk<BackendApi>()
        val client = OkHttpRealtimeClient(api, ControlledSockets(), json, sessions, "http://example.test/".toHttpUrl(), false) { 100 }
        try { client.connect(sessions.getSession()!!); fail("Insecure endpoint must fail") }
        catch (_: IllegalArgumentException) { coVerify(exactly = 0) { api.createWebSocketTicket(any()) } }
    }

    private fun client(api: BackendApi, sockets: ControlledSockets, sessions: RealtimeTestSessions) =
        OkHttpRealtimeClient(api, sockets, json, sessions, "https://example.test/".toHttpUrl(), false) { 100 }

    private fun httpFailure(code: Int) = HttpException(retrofit2.Response.error<Unit>(code, ResponseBody.create(null, "")))

    private class ControlledSockets : WebSocket.Factory {
        val requests = mutableListOf<Request>()
        val listeners = mutableListOf<WebSocketListener>()
        val sockets = mutableListOf<ControlledSocket>()
        override fun newWebSocket(request: Request, listener: WebSocketListener): WebSocket {
            requests += request
            listeners += listener
            return ControlledSocket(request).also(sockets::add)
        }
        fun open(index: Int) { listeners[index].onOpen(sockets[index], response(index, 101)) }
        fun fail(index: Int, code: Int) { listeners[index].onFailure(sockets[index], IOException("closed"), if (code == 0) null else response(index, code)) }
        fun message(index: Int, text: String) { listeners[index].onMessage(sockets[index], text) }
        private fun response(index: Int, code: Int) = Response.Builder().request(requests[index]).protocol(Protocol.HTTP_1_1).code(code).message("response").build()
    }
    private class ControlledSocket(private val originalRequest: Request) : WebSocket {
        var cancelled = false
        override fun request() = originalRequest
        override fun queueSize() = 0L
        override fun send(text: String) = true
        override fun send(bytes: ByteString) = true
        override fun close(code: Int, reason: String?) = true
        override fun cancel() { cancelled = true }
    }

    private companion object {
        val messageJson = """{"type":"conversation.message.created","conversation_id":42,"message":{"id":3,"sender_role":"consumer","content":"hello","created_on":"2026-10-02T12:00:00Z"}}"""
    }
}
