package com.loresuelvo.serviceprovider.bdd.notifications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.notifications.InstallationResult
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
        assertEquals(42, world.notifications.consumeTarget())
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
            world.permissionUseCase, world.consumeUseCase, world.local)
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

    @Entonces("el cierre local se completa aunque no se pueda contactar a LoResuelvo")
    fun credentialsRemoved() {
        assertNull(world.sessions.getSession())
        assertEquals(ProviderEntryUiState.Welcome, entry!!.uiState.value)
    }
}
