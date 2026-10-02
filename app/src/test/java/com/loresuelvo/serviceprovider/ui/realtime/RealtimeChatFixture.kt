package com.loresuelvo.serviceprovider.ui.realtime

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.auth.*
import com.loresuelvo.serviceprovider.domain.conversation.*
import com.loresuelvo.serviceprovider.domain.realtime.*
import com.loresuelvo.serviceprovider.domain.usecase.conversation.*
import com.loresuelvo.serviceprovider.domain.usecase.realtime.*
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.*
import com.loresuelvo.serviceprovider.ui.screens.messages.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeChatFixture : AutoCloseable {
    val scheduler = TestCoroutineScheduler()
    val dispatcher = StandardTestDispatcher(scheduler)
    val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val sessions = RealtimeTestSessions()
    val client = FakeRealtimeClient()
    val repository = RealtimeTestRepository()
    val player = RealtimeTestPlayer()
    val recorder = RealtimeTestRecorder()
    private val models = mutableListOf<ViewModel>()
    lateinit var conversation: ProviderConversationViewModel
    lateinit var inbox: MessagesListViewModel
    var incoming: ConversationMessage? = null
    private var owner: ProviderRealtimeViewModel? = null

    fun foreground(active: Boolean) {
        if (owner == null) owner = ProviderRealtimeViewModel(ObserveProviderSessionUseCase(sessions), ConnectProviderRealtimeUseCase(client)).also(models::add)
        owner!!.onForegroundChanged(active)
        scheduler.advanceUntilIdle()
    }

    init { Dispatchers.setMain(dispatcher) }

    fun open(pending: Boolean = false, completeLoad: Boolean = true) {
        repository.details[42] = repository.details.getValue(42).copy(status = if (pending) ConversationStatus.Pending else ConversationStatus.Active)
        conversation = ProviderConversationViewModel(
            SavedStateHandle(mapOf(Route.Conversation.argument to 42)), GetConversationByIdUseCase(repository),
            SendMessageUseCase(repository), SendMediaMessageUseCase(repository),
            object : MediaReader { override suspend fun read(uri: String): MediaUpload { repository.mediaReads++; return MediaUpload.Image(byteArrayOf(1), "image/jpeg", "photo.jpg") } },
            recorder, player, object : RecordingTimeSource() { override fun nowMillis() = scheduler.currentTime },
            ConversationRealtimeObserver(SavedStateHandle(mapOf(Route.Conversation.argument to 42)),
                ObserveProviderEventsUseCase(client, sessions), ObserveProviderSessionUseCase(sessions), ObserveRealtimeStateUseCase(client)),
        ).also(models::add)
        inbox = MessagesListViewModel(GetConversationsUseCase(repository), ObserveProviderEventsUseCase(client, sessions), ObserveProviderSessionUseCase(sessions), ObserveRealtimeStateUseCase(client)).also(models::add)
        if (completeLoad) scheduler.advanceUntilIdle() else scheduler.runCurrent()
    }

    fun receive(kind: String = "texto", conversationId: Int = 42, id: Int = 3, time: Long = 30L, session: AuthSession = sessions.getSession()!!) {
        val images = if (kind == "fotografías") listOf(MediaReference.Image("image-$id", "https://example.test/photo.jpg", "image/jpeg", "photo.jpg")) else emptyList()
        val audio = if (kind == "audio") MediaReference.Audio("audio-$id", "https://example.test/audio.webm", "audio/webm", "voice.webm", 2_000) else null
        val message = ConversationMessage(id, ConversationSender.Consumer, if (images.isEmpty() && audio == null) "message-$id" else "", time,
            if (audio == null) ConversationMessageKind.Text else ConversationMessageKind.Audio, audio ?: images.firstOrNull(), images)
        incoming = message
        repository.details[conversationId] = repository.details.getValue(conversationId).let { it.copy(messages = it.messages + message, updatedOnEpochMillis = time) }
        scope.launch { client.mutableEvents.emit(SessionEvent(session, ProviderEvent.MessageCreated(conversationId, message))) }
        scheduler.advanceUntilIdle()
    }

    val hasConversation: Boolean get() = ::conversation.isInitialized

    fun connection(connection: RealtimeState.Connection) {
        client.state.value = RealtimeState(sessions.getSession(), connection)
        scheduler.advanceUntilIdle()
    }

    fun missMessages() {
        val messages = listOf(ConversationMessage(4, ConversationSender.Consumer, "missed-4", 40),
            ConversationMessage(3, ConversationSender.Consumer, "missed-3", 30),
            ConversationMessage(3, ConversationSender.Consumer, "missed-3", 30))
        repository.details[42] = repository.details.getValue(42).let { it.copy(messages = it.messages + messages, updatedOnEpochMillis = 40) }
    }

    fun ready() = conversation.uiState.value as ProviderConversationUiState.Ready
    fun serverMessages() = ready().items.filterIsInstance<ChatListItem.ServerConfirmed>().map { it.message }
    fun inboxReady() = inbox.uiState.value as MessagesListUiState.Ready
    override fun close() {
        models.forEach { ViewModelStore().apply { put("model", it) }.clear() }
        scope.cancel()
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }
}

class RealtimeTestSessions : AuthSessionStore {
    override val sessionFlow = MutableStateFlow<AuthSession?>(AuthSession(User("provider-a", "a@example.test"), "token-a"))
    override fun getSession() = sessionFlow.value
    override fun saveSession(session: AuthSession) { sessionFlow.value = session }
    override fun clearSession() { sessionFlow.value = null }
}

class FakeRealtimeClient : RealtimeClient {
    val mutableEvents = MutableSharedFlow<SessionEvent>()
    override val events = mutableEvents.asSharedFlow()
    override val state = MutableStateFlow(RealtimeState())
    val started = mutableListOf<AuthSession>()
    var active = 0
    var maximumActive = 0
    override suspend fun connect(session: AuthSession) {
        started += session
        active++
        maximumActive = maxOf(maximumActive, active)
        state.value = RealtimeState(session, RealtimeState.Connection.Connected)
        try { awaitCancellation() } finally {
            active--
            if (state.value.session == session) state.value = RealtimeState(session, RealtimeState.Connection.Stopped)
        }
    }
}

class RealtimeTestRepository : ConversationRepository {
    val details = mutableMapOf(
        42 to ConversationDetail(42, ConversationStatus.Active, ConversationCounterpart(7, "Ana", "Perez", null),
            listOf(ConversationMessage(1, ConversationSender.Consumer, "earlier", 10), ConversationMessage(2, ConversationSender.Provider, "reply", 20)), 20),
        43 to ConversationDetail(43, ConversationStatus.Active, ConversationCounterpart(8, "Bruno", "Perez", null), emptyList(), 0),
    )
    var detailFailure: ConversationDetailOutcome.Failure? = null
    var detailCalls = 0
    var detailGate: CompletableDeferred<Unit>? = null
    var inboxGate: CompletableDeferred<Unit>? = null
    var sendGate: CompletableDeferred<Unit>? = null
    var sendCalls = 0
    var mediaReads = 0
    var inboxCalls = 0
    var inboxFailure: ConversationsOutcome.Failure? = null
    var sendOutcome: SendMessageOutcome = SendMessageOutcome.Failure.Network(java.io.IOException("offline"))
    override suspend fun getConversationById(conversationId: Int): ConversationDetailOutcome {
        detailCalls++
        val snapshot = details.getValue(conversationId)
        detailGate?.await()
        return detailFailure ?: ConversationDetailOutcome.Success(snapshot)
    }
    override suspend fun getConversations(): ConversationsOutcome {
        inboxCalls++
        val snapshot = details.values.map { Conversation(it.id, it.status, it.counterpart, it.messages.maxWithOrNull(compareBy<ConversationMessage> { message -> message.createdOnEpochMillis }.thenBy { message -> message.id }), it.updatedOnEpochMillis) }
        inboxGate?.await()
        return inboxFailure ?: ConversationsOutcome.Success(snapshot)
    }
    override suspend fun sendMessage(conversationId: Int, content: String): SendMessageOutcome {
        sendCalls++
        sendGate?.await()
        return sendOutcome
    }
    override suspend fun sendMediaMessage(conversationId: Int, media: List<MediaUpload>): SendMessageOutcome { sendCalls++; return sendOutcome }
}

class RealtimeTestPlayer : AudioPlayer {
    override val isPlaying = MutableStateFlow(false)
    override val currentPositionMillis = MutableStateFlow(0L)
    var playedUrl: String? = null
    override fun play(url: String, startPositionMillis: Long) { playedUrl = url; isPlaying.value = true }
    override fun seekTo(positionMillis: Long) { currentPositionMillis.value = positionMillis }
    override fun pause() { isPlaying.value = false }
    override fun stop() { isPlaying.value = false }
}
class RealtimeTestRecorder : AudioRecorder {
    var starts = 0
    var cancels = 0
    override fun start(): Result<Unit> { starts++; return Result.success(Unit) }
    override fun stop() = Result.success("file:///voice.webm")
    override fun cancel() { cancels++ }
    override fun discard(uri: String) = Unit
}
