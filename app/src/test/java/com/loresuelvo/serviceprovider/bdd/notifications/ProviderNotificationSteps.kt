package com.loresuelvo.serviceprovider.bdd.notifications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.notifications.*
import com.loresuelvo.serviceprovider.domain.activity.*
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.ui.turns.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.notifications.ProviderNotification
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.domain.usecase.notifications.ReceiptOutcome
import com.loresuelvo.serviceprovider.notifications.NotificationFixture
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryViewModel
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.ConversationReadingPosition
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.es.Cuando
import io.cucumber.java.es.Dado
import io.cucumber.java.es.Entonces
import org.junit.Assert.*
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest

/** Simulated receipt exercises real notification decisions; native/FCM proof belongs to final verification. */
class ProviderNotificationSteps {
    private lateinit var world: NotificationFixture
    private var result: ReceiptOutcome? = null
    private var notice: ProviderNotification? = null
    private var oldTap: String? = null
    private var reading = ConversationReadingPosition()
    private var followed = false
    private var destination: NotificationDestination? = null
    private var orderModel: ProviderTurnDetailViewModel? = null
    private var interruptionSession: AuthSession? = null
    private var errorSituation = ""
    private var routePath: String? = null
    private var entry: ProviderEntryViewModel? = null

    @Before("@US-20") fun setup() { world = NotificationFixture() }
    @After("@US-20") fun teardown() {
        entry?.let { ViewModelStore().apply { put("entry", it) }.clear() }
        world.close()
    }

    @Dado("que tengo una sesión activa y permití los avisos en este teléfono")
    @Dado("que tengo una sesión activa y permití los avisos")
    fun activeSession() { world.register(); assertTrue(world.store.read().binding!!.acknowledged) }

    @Dado("estoy {string}")
    fun situation(value: String) {
        if (value == "leyendo otra conversación") world.conversations.visible(world.sessions.getSession()!!, 43)
        else world.conversations.hide()
    }

    @Cuando("llega el aviso de un mensaje con {string} de un consumidor")
    fun message(content: String) {
        assertTrue(content in listOf("texto", "fotografías", "audio"))
        notice = world.notice()
        result = world.notifications.receive(notice!!)
    }

    @Entonces("veo un único aviso de nuevo mensaje que permite abrir esa conversación")
    fun oneMessageNotice() {
        assertEquals(ReceiptOutcome.Posted, result)
        assertEquals(1, world.display.notices.size)
        world.notifications.acceptTap(world.display.notices.single().second)
        assertEquals(com.loresuelvo.serviceprovider.domain.notifications.NotificationDestination.Conversation(42), world.notifications.consumeTarget())
    }

    @Entonces("el aviso no expone el contenido del mensaje ni datos personales")
    fun noPrivatePreview() {
        val posted = world.display.notices.single().first
        assertEquals("Nuevo mensaje", posted.title)
        assertEquals("Tenés un mensaje nuevo. Abrí LoResuelvo para responder.", posted.body)
        assertFalse(posted.body.contains("Ana"))
        assertFalse(posted.body.contains("example.test"))
    }

    @Dado("que rechacé el permiso para recibir avisos")
    fun denied() { assertTrue(world.notifications.requestPermissionOnce()); world.display.enabled = false }

    @Cuando("vuelvo a abrir LoResuelvo")
    fun reopen() { world.register(); world.chat.open(); createEntry(); world.chat.scheduler.advanceUntilIdle() }

    @Entonces("puedo seguir consultando mis mensajes y servicios")
    fun normalUse() {
        assertNotNull(world.sessions.getSession())
        assertEquals(42, world.chat.ready().detail.id)
        assertTrue(entry!!.uiState.value is ProviderEntryUiState.Home)
    }

    @Entonces("no se vuelve a pedir el permiso automáticamente")
    fun noRepeatedPermission() { assertFalse(world.notifications.requestPermissionOnce()) }

    @Entonces("desde Perfil puedo abrir los ajustes de notificaciones del teléfono")
    fun settingsAvailable() {
        val viewModel = com.loresuelvo.serviceprovider.ui.notifications.ProviderNotificationViewModel(world.registerUseCase,
            world.permissionUseCase, world.consumeUseCase, world.local, world.sessions)
        runTest(world.chat.scheduler) {
            viewModel.effects.test {
                viewModel.openSettings()
                assertEquals(com.loresuelvo.serviceprovider.ui.notifications.NotificationUiEffect.OpenSettings, awaitItem())
            }
        }
        ViewModelStore().apply { put("notifications", viewModel) }.clear()
    }

    @Dado("que estoy leyendo mi conversación con Ana")
    fun readingAna() {
        world.register(); world.chat.open()
        world.chat.conversation.onNotificationVisibilityChanged(true)
        world.chat.conversation.onPromptChange("A reply still being written")
        reading.onItems(world.chat.ready().items, false, false)
    }

    @Cuando("llega el aviso de un nuevo mensaje de Ana")
    fun pushWithoutWebSocket() {
        world.chat.missMessages()
        result = world.notifications.receive(world.notice())
        world.chat.scheduler.advanceUntilIdle()
        followed = reading.onItems(world.chat.ready().items, false, false)
    }

    @Entonces("la conversación se actualiza sin una notificación adicional del teléfono")
    fun refreshed() {
        assertEquals(ReceiptOutcome.Suppressed, result)
        assertEquals(listOf(1, 2, 3, 4), world.chat.serverMessages().map { it.id })
        assertTrue(world.display.notices.isEmpty())
    }

    @Entonces("conservo mi posición de lectura y la respuesta que estaba escribiendo")
    fun preservesComposer() {
        assertFalse(followed)
        assertEquals("A reply still being written", world.chat.ready().promptInput)
        assertEquals(listOf("1", "2"), world.chat.ready().items.take(2).map { it.key })
    }

    @Cuando("llega {string}")
    fun duplicateOrExpired(value: String) {
        if (value == "un aviso que ya recibí") {
            val notification = world.notice()
            world.notifications.receive(notification)
            result = world.notifications.receive(notification)
        } else result = world.notifications.receive(world.notice(expires = world.now))
    }

    @Entonces("no aparece una nueva notificación ni vuelve a sonar una anterior")
    fun noExtraAlert() {
        assertTrue(result == ReceiptOutcome.Duplicate || result == ReceiptOutcome.Rejected)
        assertTrue(world.display.notices.size <= 1)
    }

    @Dado("que tengo avisos visibles de mi cuenta")
    fun existingNotices() {
        world.register(); notice = world.notice()
        world.notifications.receive(notice!!)
        oldTap = world.display.notices.single().second
    }

    @Dado("el teléfono está {string}")
    fun connectivity(value: String) { world.repository.result = if (value == "sin conexión") InstallationResult.TransientFailure else InstallationResult.Applied }

    @Cuando("confirmo el cierre de sesión")
    fun confirmLogout() {
        createEntry()
        world.chat.scheduler.advanceUntilIdle()
        entry!!.requestLogout(); entry!!.confirmLogout()
        assertTrue(world.display.notices.isEmpty())
        world.chat.scheduler.advanceUntilIdle()
    }

    private fun createEntry() {
        val account = object : CurrentAccountRepository {
            override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(CurrentAccount.Provider(7, "Provider", "Account",
                "provider@example.test", Category(1, "Service"), null))
        }
        entry = ProviderEntryViewModel(world.sessions, ResolveProviderEntryUseCase(world.sessions, account), SavedStateHandle(), world.logoutUseCase, world.local)
    }

    @Entonces("desaparecen los avisos de mi cuenta en este teléfono")
    fun removed() { assertTrue(world.display.notices.isEmpty()); assertTrue(world.display.cancellations > 0) }

    @Entonces("los avisos que lleguen después para esa sesión no se muestran ni abren datos privados")
    fun oldNotificationsRejected() {
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(notice!!))
        world.notifications.acceptTap(oldTap)
        assertNull(world.notifications.consumeTarget())
    }

    @Dado("no estoy usando LoResuelvo") fun outsideApp() { world.conversations.hide() }
    @Cuando("llega el aviso de {string} de uno de mis servicios")
    fun serviceUpdate(update: String) {
        val label = when (update) {
            "una contratación con seña aprobada" -> "propuesta aceptada"
            "un turno dentro de las próximas 24 horas" -> "turno próximo"
            "la aprobación del saldo final" -> "pago final confirmado"
            else -> error("Unknown update")
        }
        notice = world.serviceNotice(label)
        result = world.notifications.receive(notice!!)
    }
    @Entonces("veo un aviso de {string} que permite abrir la orden correspondiente")
    fun servicePosted(label: String) {
        assertEquals(ReceiptOutcome.Posted, result)
        assertEquals(world.serviceNotice(label).title, world.display.notices.single().first.title)
        world.notifications.acceptTap(world.display.notices.single().second)
        assertEquals(NotificationDestination.WorkOrder(55), world.notifications.consumeTarget())
    }
    @Entonces("el aviso no expone nombres, direcciones ni importes")
    fun privateServicePreview() {
        val posted = world.display.notices.single().first
        assertFalse(posted.body.contains("Ana")); assertFalse(posted.body.contains("123456"))
        assertFalse(posted.body.contains(world.order.description))
    }
    @Dado("que no se pudo habilitar la recepción de avisos por falta de conexión")
    fun failedRegistration() {
        interruptionSession = world.sessions.getSession()
        world.repository.result = InstallationResult.TransientFailure; world.register()
        assertFalse(world.store.read().binding!!.acknowledged)
    }
    @Dado("conservé mi sesión y el permiso para recibirlos")
    fun retainedSession() { assertEquals(interruptionSession, world.sessions.getSession()); assertTrue(world.display.enabled) }
    @Cuando("vuelvo a usar LoResuelvo con conexión disponible")
    fun connectedForeground() { world.repository.result = InstallationResult.Applied; world.register() }
    @Entonces("el teléfono vuelve a quedar habilitado para recibir los próximos avisos de mi cuenta")
    fun recoveredRegistration() {
        assertTrue(world.store.read().binding!!.acknowledged)
        assertEquals(ReceiptOutcome.Posted, world.notifications.receive(world.notice()))
    }
    @Entonces("puedo seguir usando la aplicación durante la recuperación")
    fun usableRecovery() { assertEquals(interruptionSession, world.sessions.getSession()); world.chat.open(); assertEquals(42, world.chat.ready().detail.id) }
    @Dado("que tengo un aviso vigente de {string} de mi cuenta actual")
    fun currentNotice(label: String) {
        world.register()
        notice = if (label == "nuevo mensaje") world.notice() else world.serviceNotice(label)
        world.notifications.receive(notice!!); oldTap = world.display.notices.single().second
        // The final-payment destination uses the persisted detail, never payload payment state.
        if (label == "pago final confirmado") world.order = world.order.copy(status = WorkOrderStatus.Paid, paidOn = 2_000)
    }
    @Dado("LoResuelvo está {string}")
    fun appState(state: String) {
        assertTrue(state == "cerrada" || state == "abierta")
        if (state == "abierta") world.chat.open()
        else world.local.queue(null) // Cold navigation has no in-memory destination; persisted capability remains.
    }
    @Cuando("toco ese aviso")
    fun tapNotice() {
        world.notifications.acceptTap(oldTap)
        destination = world.notifications.consumeTarget()
        when (val target = destination) {
            is NotificationDestination.Conversation -> { routePath = Route.Conversation.buildPath(target.id); world.chat.open() }
            is NotificationDestination.WorkOrder -> { routePath = Route.ProviderTurnDetail.buildPath(target.id); orderModel = world.openOrder(target.id) }
            null -> createEntry().also { world.chat.scheduler.advanceUntilIdle() }
        }
    }
    @Entonces("veo {string} con la información actual de mi cuenta")
    fun currentDestination(expected: String) {
        if (expected == "la conversación") {
            assertEquals(NotificationDestination.Conversation(42), destination)
            assertEquals(world.chat.repository.details[42], world.chat.ready().detail)
        } else {
            assertEquals(NotificationDestination.WorkOrder(55), destination)
            val detail = (orderModel!!.uiState.value as ProviderTurnDetailUiState.Ready).result.detail as WorkOrderDetailOutcome.Success
            assertEquals(world.order, detail.order); assertEquals(1, world.orderCalls)
            if (expected == "la orden con el saldo pagado") assertEquals(WorkOrderStatus.Paid, detail.order.status)
        }
    }
    @Entonces("puedo volver a la aplicación sin abrir pantallas repetidas")
    fun noRepeatedDestination() {
        assertNotNull(routePath)
        world.notifications.acceptTap(oldTap); assertNull(world.notifications.consumeTarget())
        // Actual Android Back/singleTop execution is covered in final instrumented verification.
    }
    @Dado("que recibí un aviso y {string}")
    fun unavailableDestination(situation: String) {
        currentNotice("propuesta aceptada"); errorSituation = situation
        world.orderFailure = when (situation) {
            "perdí la conexión" -> WorkOrderDetailOutcome.Failure.Network(java.io.IOException("offline"))
            "el recurso ya no está disponible" -> WorkOrderDetailOutcome.Failure.NotFound
            "ya no tengo acceso al recurso" -> WorkOrderDetailOutcome.Failure.Forbidden
            "mi sesión ya no está activa" -> { world.local.invalidate(); world.sessions.clearSession(); null }
            else -> error("Unknown destination failure")
        }
    }
    @Entonces("{string}")
    fun destinationError(expected: String) {
        when (errorSituation) {
            "perdí la conexión" -> {
                assertTrue(expected.contains("reintentar"))
                assertTrue((orderModel!!.uiState.value as ProviderTurnDetailUiState.Error).failure is WorkOrderDetailOutcome.Failure.Network)
                world.orderFailure = null; orderModel!!.load(); world.chat.scheduler.advanceUntilIdle()
                assertTrue(orderModel!!.uiState.value is ProviderTurnDetailUiState.Ready)
            }
            "el recurso ya no está disponible" -> assertEquals(WorkOrderDetailOutcome.Failure.NotFound, (orderModel!!.uiState.value as ProviderTurnDetailUiState.Error).failure)
            "ya no tengo acceso al recurso" -> assertEquals(WorkOrderDetailOutcome.Failure.Forbidden, (orderModel!!.uiState.value as ProviderTurnDetailUiState.Error).failure)
            "mi sesión ya no está activa" -> { assertNull(destination); assertEquals(0, world.orderCalls); assertEquals(ProviderEntryUiState.Welcome, entry!!.uiState.value) }
            else -> error("Unexpected result step")
        }
    }
    @Entonces("no veo información privada de otra cuenta ni datos inventados")
    fun noFabricatedPrivateData() {
        if (errorSituation != "perdí la conexión") assertFalse(orderModel?.uiState?.value is ProviderTurnDetailUiState.Ready)
        assertTrue(world.store.read().binding?.recipientId == 7)
    }
    @Dado("que cerré mi sesión sin conexión y después ingresé con otra cuenta de prestador")
    fun offlineAccountSwitch() {
        currentNotice("nuevo mensaje"); world.repository.result = InstallationResult.TransientFailure
        world.logout()
        assertNull(world.sessions.getSession())
        world.sessions.saveSession(AuthSession(User("provider-b", "b@example.test"), "token-b"))
    }
    @Dado("la nueva cuenta quedó habilitada para recibir avisos en este teléfono")
    fun newAccountEnabled() {
        world.repository.result = InstallationResult.Applied
        runTest(world.chat.scheduler) { world.notifications.enter(8, "es") }
        assertEquals(8, world.store.read().binding!!.recipientId)
        assertTrue(world.store.read().binding!!.acknowledged)
    }
    @Cuando("llega un aviso pendiente de la cuenta anterior")
    fun oldAccountReceipt() { result = world.notifications.receive(notice!!) }
    @Entonces("no se muestra ese aviso ni permite entrar a la cuenta anterior")
    fun rejectedOldAccount() {
        assertEquals(ReceiptOutcome.Rejected, result); assertTrue(world.display.notices.isEmpty())
        world.notifications.acceptTap(oldTap); assertNull(world.notifications.consumeTarget())
    }
    @Entonces("sigo pudiendo recibir los avisos de mi cuenta actual")
    fun receivesNewAccount() {
        assertEquals(ReceiptOutcome.Posted, world.notifications.receive(world.notice("message:124:8").copy(recipientId = 8)))
    }

    @Entonces("el cierre local se completa aunque no se pueda contactar a LoResuelvo")
    fun credentialsRemoved() {
        assertNull(world.sessions.getSession())
        assertEquals(ProviderEntryUiState.Welcome, entry!!.uiState.value)
    }
}
