package com.loresuelvo.serviceprovider.bdd.messaging.realtime

import com.loresuelvo.serviceprovider.domain.conversation.MediaReference
import com.loresuelvo.serviceprovider.ui.realtime.RealtimeChatFixture
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.es.*
import org.junit.Assert.*

class ProviderRealtimeChatSteps {
    private lateinit var world: RealtimeChatFixture
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
}
