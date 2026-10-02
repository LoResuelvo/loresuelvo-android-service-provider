package com.loresuelvo.serviceprovider.bdd.messaging.realtime

import com.loresuelvo.serviceprovider.domain.conversation.MediaReference
import com.loresuelvo.serviceprovider.ui.realtime.RealtimeChatFixture
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.ConversationReadingPosition
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.es.*
import org.junit.Assert.*
import kotlinx.coroutines.launch

// One story owns its fixture and production assertions; recovery steps are the next split if this glue grows.
class ProviderRealtimeChatSteps {
    private lateinit var world: RealtimeChatFixture
    private var reading = ConversationReadingPosition()
    private var atBottom = false
    private var followed = false
    private var originalItems = emptyList<String>()
    private var draft = ""
    private var selectedTarget: String? = null
    private var beforeForeignMessage: ProviderConversationUiState? = null

    @Before("@batch1 or @batch2 or @batch3") fun setUp() { world = RealtimeChatFixture() }
    @After("@batch1 or @batch2 or @batch3") fun tearDown() { world.close() }

    @Dado("que inicié sesión como prestador")
    fun authenticated() { assertNotNull(world.sessions.getSession()) }

    @Dado("que estoy al final de una conversación activa con Ana")
    fun openAtEnd() { world.open() }

    @Dado("que tengo abierta la conversación con Ana")
    fun openAna() { world.open(); beforeForeignMessage = world.conversation.uiState.value }

    @Dado("tengo otra conversación con Bruno")
    fun otherConversation() { assertTrue(world.inboxReady().conversations.any { it.counterpart.name == "Bruno" }) }

    @Dado("que tengo abierta una solicitud de Ana que todavía no acepté")
    fun openPending() { world.open(pending = true) }

    @Cuando("Ana me envía un mensaje de {string}")
    fun incoming(kind: String) { world.receive(kind) }

    @Cuando("Bruno me envía un mensaje")
    fun brunoSends() { world.receive(conversationId = 43) }

    @Cuando("Ana me envía información adicional por el chat")
    fun additionalInformation() { world.receive() }

    @Dado("que estoy leyendo el último mensaje de Ana")
    fun readingLatest() { arrangeReading(true) }

    @Dado("que estoy leyendo mensajes anteriores de Ana")
    fun readingHistory() { arrangeReading(false) }

    private fun arrangeReading(bottom: Boolean) {
        world.open()
        reading = ConversationReadingPosition()
        reading.onItems(world.ready().items, true, false)
        atBottom = bottom
        originalItems = world.ready().items.map { it.key }
    }

    @Dado("tengo una respuesta escrita sin enviar")
    @Dado("que tengo una respuesta escrita sin enviar")
    fun unsentReply() {
        if (!world.hasConversation) world.open()
        draft = "A reply still being written"
        world.conversation.onPromptChange(draft)
    }

    @Cuando("Ana envía un nuevo mensaje")
    fun arrivalWhileReading() {
        world.receive()
        followed = reading.onItems(world.ready().items, atBottom, false)
    }

    @Entonces("veo el mensaje nuevo al final de la conversación")
    fun latestAtEnd() { assertEquals(world.incoming, world.serverMessages().last()) }

    @Entonces("no necesito desplazarme para encontrarlo")
    fun followsLatest() { assertTrue(followed); assertFalse(reading.hasNewMessage) }

    @Entonces("conservo mi posición de lectura y mi respuesta escrita")
    fun preservesReadingAndDraft() {
        assertFalse(followed)
        assertEquals(originalItems, world.ready().items.take(originalItems.size).map { it.key })
        assertEquals(draft, world.ready().promptInput)
    }

    @Entonces("veo el aviso Nuevo mensaje")
    fun newMessageNotice() { assertTrue(reading.hasNewMessage) }

    @Dado("veo el aviso Nuevo mensaje porque recibí dos mensajes más")
    fun twoArrivals() {
        arrivalWhileReading()
        world.receive(id = 4, time = 40)
        followed = reading.onItems(world.ready().items, atBottom, false)
        assertTrue(reading.hasNewMessage)
    }

    @Cuando("selecciono Nuevo mensaje")
    fun selectNotice() {
        selectedTarget = reading.selectNewMessages(world.ready().items)?.let { world.ready().items[it].key }
    }

    @Entonces("veo el mensaje más reciente de Ana")
    fun newestMessage() {
        assertEquals(world.incoming!!.id.toString(), selectedTarget)
        assertEquals(world.incoming, world.serverMessages().last())
    }

    @Entonces("el aviso desaparece")
    fun noticeGone() { assertFalse(reading.hasNewMessage) }

    @Entonces("los dos mensajes recibidos permanecen en la conversación")
    fun bothArrivalsRemain() { assertEquals(listOf(1, 2, 3, 4), world.serverMessages().map { it.id }) }

    @Entonces("veo el nuevo mensaje de Ana sin salir de la conversación")
    fun seeNewMessage() { assertTrue(world.serverMessages().contains(world.incoming)); assertEquals(42, world.ready().detail.id) }

    @Entonces("conservo el historial ordenado y la hora de cada mensaje")
    fun orderedHistory() {
        val messages = world.serverMessages()
        assertEquals(listOf(1, 2, 3), messages.map { it.id })
        assertEquals(listOf(10L, 20L, 30L), messages.map { it.createdOnEpochMillis })
    }

    @Entonces("puedo consultar su contenido con las acciones habituales")
    fun inspectContent() {
        val message = world.serverMessages().last()
        when (val media = message.media) {
            is MediaReference.Audio -> {
                world.conversation.onPlayAudio(message.id.toString(), media.url)
                assertEquals(media.url, world.player.playedUrl)
            }
            is MediaReference.Image -> { assertEquals("https://example.test/photo.jpg", message.images.single().url) }
            null -> assertEquals("message-3", message.content)
        }
    }

    @Entonces("la conversación abierta conserva únicamente los mensajes de Ana y los míos")
    fun keepAnaHistory() { assertEquals(listOf(1, 2), world.serverMessages().map { it.id }) }

    @Entonces("la bandeja refleja el nuevo mensaje en la conversación con Bruno")
    fun inboxUpdated() { assertEquals(world.incoming, world.inboxReady().conversations.single { it.id == 43 }.lastMessage) }

    @Entonces("no aparece un aviso de nuevo mensaje dentro de la conversación con Ana")
    fun noForeignNotice() { assertSame(beforeForeignMessage, world.conversation.uiState.value) }

    @Entonces("puedo leer esa información sin volver a abrir la solicitud")
    fun readPending() { assertTrue(world.serverMessages().contains(world.incoming)) }

    @Entonces("se indica que debo aceptar la solicitud para responder")
    fun pendingRestriction() { assertFalse(world.ready().composerAllowed) }

    @Entonces("no puedo enviar mensajes ni adjuntos hasta aceptarla")
    fun cannotSend() {
        world.conversation.onPromptChange("reply")
        world.conversation.onImagesPicked(listOf("content://photo"))
        world.conversation.onStartRecording()
        world.conversation.onSendClick()
        world.scheduler.advanceUntilIdle()
        assertEquals(0, world.repository.sendCalls)
        assertEquals(0, world.repository.mediaReads)
        assertEquals(0, world.recorder.starts)
        assertEquals("", world.ready().promptInput)
    }
    private var backgroundInterruption = false
    private var previousSession: com.loresuelvo.serviceprovider.domain.auth.AuthSession? = null

    @Dado("Ana me envió mensajes mientras {string}")
    fun missedActivity(interruption: String) {
        assertTrue(interruption == "mi conexión estaba interrumpida" || interruption == "estaba usando otra aplicación")
        backgroundInterruption = interruption == "estaba usando otra aplicación"
        if (backgroundInterruption) { world.foreground(true); world.foreground(false) }
        else world.connection(com.loresuelvo.serviceprovider.domain.realtime.RealtimeState.Connection.Retrying)
        world.missMessages()
    }

    @Cuando("vuelvo a usar la conversación con conexión disponible")
    fun reconnect() {
        if (backgroundInterruption) world.foreground(true)
        else world.connection(com.loresuelvo.serviceprovider.domain.realtime.RealtimeState.Connection.Connected)
    }

    @Entonces("aparecen los mensajes recibidos durante la interrupción sin recargar manualmente")
    fun recoveredMessages() { assertEquals(listOf(1, 2, 3, 4), world.serverMessages().map { it.id }) }

    @Entonces("cada mensaje aparece una sola vez en orden cronológico")
    fun uniqueChronologicalMessages() {
        val messages = world.serverMessages()
        assertEquals(messages.size, messages.map { it.id }.distinct().size)
        assertEquals(messages.sortedWith(compareBy({ it.createdOnEpochMillis }, { it.id })), messages)
    }

    @Entonces("conservo mi respuesta escrita mientras siga abierta la misma sesión de la aplicación")
    @Entonces("conservo mi respuesta escrita")
    fun retainedDraft() { assertEquals(draft, world.ready().promptInput) }

    @Entonces("la bandeja refleja la actividad recuperada")
    fun recoveredInbox() { assertEquals(4, world.inboxReady().conversations.single { it.id == 42 }.lastMessage!!.id) }

    @Dado("que veo el historial de Ana y una respuesta escrita sin enviar")
    fun historyAndDraft() { world.open(); unsentReply() }

    @Dado("no se pudo recuperar la actividad reciente de esa conversación")
    fun failedRefresh() {
        world.missMessages()
        world.repository.detailFailure = com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome.Failure.Network(java.io.IOException("offline"))
        reconnect()
        assertNotNull(world.ready().refreshFailure)
        assertEquals(listOf(1, 2), world.serverMessages().map { it.id })
    }

    @Cuando("elijo reintentar con conexión disponible")
    fun retryRefresh() {
        world.repository.detailFailure = null
        world.conversation.onRetryLoad()
        world.scheduler.advanceUntilIdle()
    }

    @Entonces("veo el historial actualizado sin mensajes repetidos")
    fun updatedHistory() { recoveredMessages(); uniqueChronologicalMessages() }

    @Entonces("desaparece el aviso de actualización pendiente")
    fun refreshNoticeCleared() { assertNull(world.ready().refreshFailure); assertFalse(world.ready().refreshing) }

    @Dado("que cerré mi sesión de prestador mientras tenía abierta una conversación")
    fun logout() {
        world.open()
        previousSession = world.sessions.getSession()
        world.sessions.clearSession()
        world.scheduler.advanceUntilIdle()
    }

    @Dado("después ingresé con otra cuenta de prestador")
    fun loginOtherAccount() {
        world.repository.details.clear()
        world.repository.details[43] = com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail(43,
            com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus.Active,
            com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart(8, "Bruno", "Perez", null), emptyList(), 0)
        world.sessions.saveSession(com.loresuelvo.serviceprovider.domain.auth.AuthSession(
            com.loresuelvo.serviceprovider.domain.auth.User("provider-b", "b@example.test"), "token-b"))
        world.scheduler.advanceUntilIdle()
    }

    @Cuando("llega un mensaje dirigido a mi cuenta anterior")
    fun previousAccountMessage() {
        world.scope.launch {
            world.client.mutableEvents.emit(com.loresuelvo.serviceprovider.domain.realtime.SessionEvent(previousSession!!,
                com.loresuelvo.serviceprovider.domain.realtime.ProviderEvent.MessageCreated(42,
                    com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage(99,
                        com.loresuelvo.serviceprovider.domain.conversation.ConversationSender.Consumer, "private-old-account", 99))))
        }
        world.scheduler.advanceUntilIdle()
    }

    @Entonces("no veo ese mensaje ni el historial privado de la cuenta anterior")
    fun privateHistoryHidden() {
        assertEquals(com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome.Failure.Unauthorized,
            (world.conversation.uiState.value as ProviderConversationUiState.Error).failure)
        assertFalse(world.conversation.canStartComposerOperation())
    }

    @Entonces("puedo consultar las conversaciones de mi cuenta actual")
    fun ownInboxAvailable() { assertEquals(listOf(43), world.inboxReady().conversations.map { it.id }) }

}
