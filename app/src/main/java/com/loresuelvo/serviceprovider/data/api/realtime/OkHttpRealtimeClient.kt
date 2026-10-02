package com.loresuelvo.serviceprovider.data.api.realtime

import com.loresuelvo.serviceprovider.data.api.BackendApi
import com.loresuelvo.serviceprovider.data.api.mapper.decodeProviderEvent
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.realtime.RealtimeClient
import com.loresuelvo.serviceprovider.domain.realtime.RealtimeState
import com.loresuelvo.serviceprovider.domain.realtime.RealtimeState.Connection
import com.loresuelvo.serviceprovider.domain.realtime.SessionEvent
import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.HttpException

/** Tickets use the authenticated HTTP client; socket upgrades never carry bearer credentials. */
class OkHttpRealtimeClient(
    private val api: BackendApi,
    private val socketClient: WebSocket.Factory,
    private val json: Json,
    private val sessions: AuthSessionStore,
    private val baseUrl: HttpUrl,
    private val allowCleartext: Boolean,
    private val retryDelayMillis: (Int) -> Long = { attempt ->
        (1_000L shl attempt.coerceAtMost(4)) + Random.nextLong(0L, 501L)
    },
) : RealtimeClient {
    private val mutableEvents = MutableSharedFlow<SessionEvent>()
    override val events = mutableEvents.asSharedFlow()
    private val mutableState = MutableStateFlow(RealtimeState())
    override val state = mutableState.asStateFlow()
    @Volatile private var generation = 0L
    private val replacement = Mutex()
    private var activeConnection: Job? = null

    override suspend fun connect(session: AuthSession) {
        val ownJob = currentCoroutineContext()[Job] ?: error("Realtime requires a coroutine job")
        val ownGeneration = replacement.withLock {
            activeConnection?.takeIf { it != ownJob }?.cancelAndJoin()
            activeConnection = ownJob
            ++generation
        }
        fun current() = generation == ownGeneration && sessions.getSession() == session
        require(baseUrl.isHttps || allowCleartext) { "Realtime requires a secure endpoint" }
        var attempt = 0
        try {
            while (currentCoroutineContext().isActive && current()) {
                mutableState.value = RealtimeState(session, if (attempt == 0) Connection.Connecting else Connection.Retrying)
                val ticket = try {
                    api.createWebSocketTicket(session).ticket.also { require(it.isNotBlank()) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: HttpException) {
                    if (!current()) return
                    if (failure.code() == 401) {
                        mutableState.value = RealtimeState(session, Connection.Unauthorized)
                        if (sessions.getSession() == session) sessions.clearSession()
                        awaitCancellation()
                    }
                    if (failure.code() in 400..499 && failure.code() != 408 && failure.code() != 429) {
                        mutableState.value = RealtimeState(session, Connection.Unavailable)
                        awaitCancellation()
                    }
                    null
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    if (current()) mutableState.value = RealtimeState(session, Connection.Unavailable)
                    awaitCancellation()
                }
                if (!current()) return
                if (ticket != null) {
                    val result = receive(session, ticket, ownGeneration)
                    if (result.opened) attempt = 0
                    val code = result.code
                    if (!current()) return
                    // A ticket may expire before the upgrade. Acquire a new one; never log out on upgrade 401.
                    if (code in 400..499 && code != 401 && code != 408 && code != 429) {
                        mutableState.value = RealtimeState(session, Connection.Unavailable)
                        awaitCancellation()
                    }
                }
                attempt = (attempt + 1).coerceAtMost(MAX_BACKOFF_ATTEMPT)
                mutableState.value = RealtimeState(session, Connection.Retrying)
                delay(retryDelayMillis(attempt - 1))
            }
        } finally {
            if (generation == ownGeneration) {
                ++generation
                mutableState.value = RealtimeState()
            }
        }
    }

    private suspend fun receive(session: AuthSession, ticket: String, ownGeneration: Long): AttemptResult {
        var opened = false
        val incoming = Channel<SocketSignal>(64)
        fun current() = generation == ownGeneration && sessions.getSession() == session
        val url = baseUrl.newBuilder().encodedPath("/ws").query(null)
            .addQueryParameter("ticket", ticket).addQueryParameter("role", "provider").build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!current()) { webSocket.cancel(); return }
                incoming.trySend(SocketSignal.Open)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!current()) return
                if (incoming.trySend(SocketSignal.Text(text)).isFailure) {
                    // Overflow closes the attempt so recovery can fetch authoritative state instead of silently dropping events.
                    incoming.close()
                    webSocket.cancel()
                }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { incoming.close() }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                incoming.trySend(SocketSignal.Failed(response?.code ?: 0))
                incoming.close()
            }
        }
        val socket = socketClient.newWebSocket(Request.Builder().url(url).build(), listener)
        try {
            for (signal in incoming) {
                if (!current()) return AttemptResult(0, opened)
                when (signal) {
                    SocketSignal.Open -> {
                        opened = true
                        mutableState.value = RealtimeState(session, Connection.Connected)
                    }
                    is SocketSignal.Text -> json.decodeProviderEvent(signal.value)?.let {
                        if (current()) mutableEvents.emit(SessionEvent(session, it))
                    }
                    is SocketSignal.Failed -> return AttemptResult(signal.code, opened)
                }
            }
            return AttemptResult(0, opened)
        } finally {
            incoming.cancel()
            socket.cancel()
        }
    }

    private data class AttemptResult(val code: Int, val opened: Boolean)

    private sealed interface SocketSignal {
        data object Open : SocketSignal
        data class Text(val value: String) : SocketSignal
        data class Failed(val code: Int) : SocketSignal
    }

    private companion object { const val MAX_BACKOFF_ATTEMPT = 5 }
}
