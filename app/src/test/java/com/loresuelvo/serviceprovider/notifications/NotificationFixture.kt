package com.loresuelvo.serviceprovider.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.notifications.*
import com.loresuelvo.serviceprovider.domain.usecase.notifications.*
import com.loresuelvo.serviceprovider.ui.realtime.RealtimeChatFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

/** All receipt here is simulated; this fixture never asserts Firebase transport delivery. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationFixture : AutoCloseable {
    val conversations = NotificationConversationState()
    val chat = RealtimeChatFixture(conversations)
    val sessions = chat.sessions
    val store = MemoryNotificationStore()
    val display = RecordingNotificationDisplay()
    val repository = RecordingInstallationRepository()
    var token: String? = "synthetic-fcm-token"
    var now = 1_000L
    val local = NotificationLocalSession(store, display, conversations)
    private val tokens = object : NotificationTokenSource {
        override suspend fun token() = token
    }
    val registerUseCase = RegisterNotificationInstallationUseCase(sessions, store, repository, tokens, local)
    val permissionUseCase = RequestNotificationPermissionUseCase(store)
    val consumeUseCase = ConsumeNotificationTargetUseCase(sessions, store, local, NotificationClock { now })
    val logoutUseCase = LogoutNotificationSessionUseCase(sessions, store, repository, local)
    private val receiveUseCase = ReceiveProviderNotificationUseCase(sessions, store, display, local, conversations, NotificationClock { now })
    private val acceptUseCase = AcceptNotificationTapUseCase(sessions, store, local, NotificationClock { now })
    val notifications = NotificationActions()

    inner class NotificationActions {
        suspend fun enter(recipientId: Int, locale: String) = registerUseCase(recipientId, locale)
        suspend fun logout() = logoutUseCase(sessions.getSession())
        fun requestPermissionOnce() = permissionUseCase()
        fun receive(notice: ProviderNotification) = receiveUseCase(notice)
        fun acceptTap(tapId: String?) = acceptUseCase(tapId)
        fun consumeTarget() = consumeUseCase()
    }

    fun register() = runTest(chat.scheduler) { notifications.enter(7, "es") }
    fun logout() = runTest(chat.scheduler) { notifications.logout() }
    fun notice(eventId: String = "message:123:7", expires: Long = now + 60_000): ProviderNotification {
        val state = store.read()
        return ProviderNotification(eventId, 42, 7, state.id, checkNotNull(state.binding).id,
            "Nuevo mensaje", "Tenés un mensaje nuevo. Abrí LoResuelvo para responder.", expires)
    }
    override fun close() = chat.close()
}

class MemoryNotificationStore : NotificationStateStore {
    var value = NotificationInstallation("10000000-0000-4000-8000-000000000001", "20000000-0000-4000-8000-000000000002")
    var writable = true
    override fun read() = value
    override fun write(state: NotificationInstallation): Boolean { if (writable) value = state; return writable }
}

class RecordingNotificationDisplay : NotificationDisplay {
    var enabled = true
    var cancellations = 0
    val notices = mutableListOf<Pair<ProviderNotification, String>>()
    var onCanPost: (() -> Unit)? = null
    override fun canPost(): Boolean { onCanPost?.invoke(); return enabled }
    override fun post(notice: ProviderNotification, tapId: String): Boolean { notices += notice to tapId; return true }
    override fun cancelAll() { cancellations++; notices.clear() }
}

class RecordingInstallationRepository : InstallationRepository {
    val registered = mutableListOf<NotificationInstallation>()
    val removed = mutableListOf<NotificationInstallation>()
    var result: InstallationResult = InstallationResult.Applied
    var removalGate: CompletableDeferred<Unit>? = null
    var onRemove: ((AuthSession) -> Unit)? = null
    override suspend fun register(installation: NotificationInstallation, token: String, locale: String, session: AuthSession): InstallationResult {
        registered += installation
        return result
    }
    override suspend fun remove(installation: NotificationInstallation, session: AuthSession): InstallationResult {
        removed += installation
        onRemove?.invoke(session)
        removalGate?.await()
        return result
    }
}
