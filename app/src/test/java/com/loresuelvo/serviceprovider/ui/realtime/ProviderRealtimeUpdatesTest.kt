package com.loresuelvo.serviceprovider.ui.realtime

import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.conversation.*
import com.loresuelvo.serviceprovider.domain.usecase.realtime.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.*
import com.loresuelvo.serviceprovider.ui.screens.messages.MessagesListUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderRealtimeUpdatesTest {
    @Test fun `subscribe before initial read and merge duplicate events in deterministic order`() {
        RealtimeChatFixture().use { world ->
            val gate = CompletableDeferred<Unit>()
            world.repository.detailGate = gate
            world.open(completeLoad = false)
            world.receive(id = 4, time = 20)
            world.receive(id = 3, time = 20)
            world.receive(id = 3, time = 20)
            gate.complete(Unit)
            world.scheduler.advanceUntilIdle()
            assertEquals(listOf(1, 2, 3, 4), world.serverMessages().map { it.id })
        }
    }

    @Test fun `incoming events retain draft media failed and pending sends`() {
        RealtimeChatFixture().use { world ->
            world.open()
            world.conversation.onPromptChange("failed")
            world.conversation.onSendClick()
            world.scheduler.advanceUntilIdle()
            val failed = world.ready().items.filterIsInstance<ChatListItem.LocalFailed>().single()
            world.repository.sendGate = CompletableDeferred()
            world.conversation.onPromptChange("pending")
            world.conversation.onSendClick()
            world.scheduler.runCurrent()
            world.receive()
            assertTrue(world.ready().items.contains(failed))
            assertEquals(1, world.ready().items.filterIsInstance<ChatListItem.LocalPending>().size)
            world.repository.sendGate!!.complete(Unit)
            world.scheduler.advanceUntilIdle()
            world.conversation.onPromptChange("unsent draft")
            world.conversation.onImagesPicked(listOf("content://photo"))
            world.scheduler.advanceUntilIdle()
            val staged = world.ready().pendingImages
            world.receive(id = 4)
            assertEquals("unsent draft", world.ready().promptInput)
            assertEquals(staged, world.ready().pendingImages)
        }
    }

    @Test fun `HTTP send confirmation merges an event of the same server identity`() {
        RealtimeChatFixture().use { world ->
            world.open()
            world.repository.sendGate = CompletableDeferred()
            world.conversation.onPromptChange("send")
            world.conversation.onSendClick()
            world.scheduler.runCurrent()
            world.receive(id = 3)
            world.repository.sendOutcome = SendMessageOutcome.Success(world.incoming!!)
            world.repository.sendGate!!.complete(Unit)
            world.scheduler.advanceUntilIdle()
            assertEquals(listOf(1, 2, 3), world.serverMessages().map { it.id })
            assertFalse(world.ready().sending)
        }
    }

    @Test fun `inbox coalesces invalidations during an active request without losing last update`() {
        RealtimeChatFixture().use { world ->
            world.repository.inboxGate = CompletableDeferred()
            world.open(completeLoad = false)
            world.receive(conversationId = 43, id = 3)
            world.receive(conversationId = 43, id = 4)
            assertEquals(1, world.repository.inboxCalls)
            world.repository.inboxGate!!.complete(Unit)
            world.scheduler.advanceUntilIdle()
            assertEquals(2, world.repository.inboxCalls)
            assertEquals(4, world.inboxReady().conversations.single { it.id == 43 }.lastMessage!!.id)
        }
    }

    @Test fun `logout cancels private reads and old account events never populate replacement session`() {
        RealtimeChatFixture().use { world ->
            val oldSession = world.sessions.getSession()!!
            world.repository.detailGate = CompletableDeferred()
            world.open(completeLoad = false)
            world.sessions.clearSession()
            world.scheduler.runCurrent()
            world.sessions.saveSession(AuthSession(User("provider-b", "b@example.test"), "token-b"))
            world.scheduler.runCurrent()
            world.receive(session = oldSession)
            world.repository.detailGate!!.complete(Unit)
            world.scheduler.advanceUntilIdle()
            assertEquals(ConversationDetailOutcome.Failure.Unauthorized, (world.conversation.uiState.value as ProviderConversationUiState.Error).failure)
            assertTrue(world.inbox.uiState.value is MessagesListUiState.Ready)
        }
    }

    @Test fun `reconnection reconciles missed messages while preserving draft and events during read`() {
        RealtimeChatFixture().use { world ->
            world.open()
            world.conversation.onPromptChange("draft")
            val missed = ConversationMessage(3, ConversationSender.Consumer, "missed", 30)
            world.repository.details[42] = world.repository.details.getValue(42).copy(messages = world.repository.details.getValue(42).messages + missed)
            val gate = CompletableDeferred<Unit>()
            world.repository.detailGate = gate
            world.client.state.value = com.loresuelvo.serviceprovider.domain.realtime.RealtimeState(world.sessions.getSession(), com.loresuelvo.serviceprovider.domain.realtime.RealtimeState.Connection.Connected)
            world.scheduler.runCurrent()
            world.receive(id = 4, time = 40)
            gate.complete(Unit)
            world.scheduler.advanceUntilIdle()
            assertEquals(listOf(1, 2, 3, 4), world.serverMessages().map { it.id })
            assertEquals(world.serverMessages(), world.ready().detail.messages)
            assertEquals("draft", world.ready().promptInput)
            assertEquals(4, world.inboxReady().conversations.single { it.id == 42 }.lastMessage!!.id)
        }
    }

    @Test fun `forbidden authoritative refresh clears private content and composer`() {
        RealtimeChatFixture().use { world ->
            world.open()
            world.conversation.onPlayAudio("1", "https://example.test/audio.webm")
            world.conversation.onStartRecording()
            val cancelsBefore = world.recorder.cancels
            world.player.isPlaying.value = true
            world.repository.detailFailure = ConversationDetailOutcome.Failure.Server(403, "Forbidden")
            world.client.state.value = com.loresuelvo.serviceprovider.domain.realtime.RealtimeState(world.sessions.getSession(), com.loresuelvo.serviceprovider.domain.realtime.RealtimeState.Connection.Connected)
            world.scheduler.advanceUntilIdle()
            assertTrue(world.conversation.uiState.value is ProviderConversationUiState.Error)
            assertFalse(world.conversation.canStartComposerOperation())
            assertFalse(world.player.isPlaying.value)
            assertTrue(world.recorder.cancels > cancelsBefore)
        }
    }

    @Test fun `activity owner shares one connection and restarts after background and later login`() {
        RealtimeChatFixture().use { world ->
            val owner = ProviderRealtimeViewModel(ObserveProviderSessionUseCase(world.sessions), ConnectProviderRealtimeUseCase(world.client))
            try {
                world.scheduler.runCurrent()
                assertEquals(0, world.client.active)
                owner.onForegroundChanged(true)
                world.scheduler.runCurrent()
                world.open()
                assertEquals(1, world.client.active)
                owner.onForegroundChanged(false)
                world.scheduler.runCurrent()
                assertEquals(0, world.client.active)
                owner.onForegroundChanged(true)
                world.scheduler.runCurrent()
                world.sessions.clearSession()
                world.scheduler.runCurrent()
                world.sessions.saveSession(AuthSession(User("provider-b", "b@example.test"), "token-b"))
                world.scheduler.runCurrent()
                assertEquals(3, world.client.started.size)
                assertEquals(1, world.client.maximumActive)
                assertEquals("provider-b", world.client.started.last().user.id)
            } finally { ViewModelStore().apply { put("owner", owner) }.clear() }
        }
    }
}
