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
    init { sessions.notificationCleanup = local }
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
        suspend fun enter(recipientId: Int, locale: String) = registerUseCase(verifyProvider(recipientId), locale)
        suspend fun logout() = logoutUseCase(sessions.getSession())
        fun requestPermissionOnce() = permissionUseCase()
        fun receive(notice: ProviderNotification) = receiveUseCase(notice)
        fun acceptTap(tapId: String?) = acceptUseCase(tapId)
        fun consumeTarget() = consumeUseCase()
    }

    fun verifyProvider(id: Int = 7): VerifiedNotificationAccount {
        val session = checkNotNull(sessions.getSession())
        local.verify(session, id)
        return VerifiedNotificationAccount.from(session, id)
    }

    fun register() = runTest(chat.scheduler) { notifications.enter(7, "es") }
    fun logout() = runTest(chat.scheduler) { notifications.logout() }
    fun notice(eventId: String = "message:123:7", expires: Long = now + 60_000): ProviderNotification {
        val state = store.read()
        return ProviderNotification(eventId, NotificationDestination.Conversation(42), 7, state.id, checkNotNull(state.binding).id,
            "Nuevo mensaje", "Tenés un mensaje nuevo. Abrí LoResuelvo para responder.", expires)
    }
    var order = com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail(55, 10, 3, 7, 123456,
        1_000_000, "Current authorized order", com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus.Scheduled, null)
    var orderFailure: com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome.Failure? = null
    var orderCalls = 0
    private var orderModel: com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailViewModel? = null
    fun openOrder(id: Int): com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailViewModel {
        val orders = object : com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository {
            override suspend fun getWorkOrders() = com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome.Success(
                emptyList<com.loresuelvo.serviceprovider.domain.activity.WorkOrder>())
            override suspend fun getWorkOrder(id: Int): com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome {
                orderCalls++; check(id == order.id)
                return orderFailure ?: com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome.Success(order)
            }
        }
        val proposals = object : com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository {
            override suspend fun list() = com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome.Success(emptyList())
            override suspend fun create(proposal: com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal): com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome = error("Not used")
        }
        return com.loresuelvo.serviceprovider.ui.turns.ProviderTurnDetailViewModel(androidx.lifecycle.SavedStateHandle(mapOf("turnId" to id)),
            com.loresuelvo.serviceprovider.domain.usecase.activity.GetProviderWorkOrderDetailUseCase(orders),
            com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase(proposals), sessions).also {
                orderModel = it; chat.scheduler.advanceUntilIdle()
            }
    }
    fun serviceNotice(label: String): ProviderNotification {
        val (type, title, body) = when (label) {
            "propuesta aceptada" -> Triple("service_proposal_accepted", "Propuesta aceptada", "Se confirmó una contratación.")
            "turno próximo" -> Triple("work_order_close_to_scheduled_time", "Turno próximo", "Tenés un servicio programado dentro de las próximas 24 horas.")
            "pago final confirmado" -> Triple("work_order_final_payment_approved", "Pago final confirmado", "Se aprobó el pago del saldo de tu servicio.")
            else -> error("Unknown service notice")
        }
        val payload = ProviderNotificationParserTest.payload() + mapOf(
            "event_id" to "notification:$type:123", "type" to type, "resource_type" to "work_order",
            "destination" to "work_order", "resource_id" to "55", "title" to title, "body" to body,
            "installation_id" to store.read().id, "binding_id" to checkNotNull(store.read().binding).id,
            "expires_at" to java.time.Instant.ofEpochMilli(now + 60_000).toString())
        return checkNotNull(com.loresuelvo.serviceprovider.data.api.mapper.ProviderNotificationParser().parse(payload))
    }
    override fun close() {
        orderModel?.let { androidx.lifecycle.ViewModelStore().apply { put("order", it) }.clear() }
        chat.close()
    }
}

class MemoryNotificationStore : NotificationStateStore {
    var value = NotificationInstallation("10000000-0000-4000-8000-000000000001", "20000000-0000-4000-8000-000000000002")
    var writable = true
    var onRead: (() -> Unit)? = null
    override fun read(): NotificationInstallation { onRead?.invoke(); return value }
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
    var registrationGate: CompletableDeferred<Unit>? = null
    var removalGate: CompletableDeferred<Unit>? = null
    var onRemove: ((AuthSession) -> Unit)? = null
    override suspend fun register(installation: NotificationInstallation, token: String, locale: String, session: AuthSession): InstallationResult {
        registered += installation
        registrationGate?.await()
        return result
    }
    override suspend fun remove(installation: NotificationInstallation, session: AuthSession): InstallationResult {
        removed += installation
        onRemove?.invoke(session)
        removalGate?.await()
        return result
    }
}
