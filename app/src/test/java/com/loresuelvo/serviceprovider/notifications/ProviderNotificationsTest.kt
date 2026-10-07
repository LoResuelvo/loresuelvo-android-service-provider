package com.loresuelvo.serviceprovider.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.notifications.InstallationResult
import com.loresuelvo.serviceprovider.domain.notifications.NotificationTokenSource
import com.loresuelvo.serviceprovider.domain.notifications.NotificationClock
import com.loresuelvo.serviceprovider.domain.usecase.notifications.ReceiveProviderNotificationUseCase
import com.loresuelvo.serviceprovider.domain.usecase.notifications.ReceiptOutcome
import com.loresuelvo.serviceprovider.ui.screens.conversation.ConversationReadingPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderNotificationsTest {
    @Test fun receipt_deduplicates_after_recreation_and_expired_events_never_post() = NotificationFixture().use { world ->
        world.register()
        val notice = world.notice()
        assertEquals(ReceiptOutcome.Posted, world.notifications.receive(notice))
        val recreated = ReceiveProviderNotificationUseCase(world.sessions, world.store, world.display, world.local,
            world.conversations, NotificationClock { world.now })
        assertEquals(ReceiptOutcome.Duplicate, recreated(notice))
        assertEquals(ReceiptOutcome.Rejected, recreated(world.notice("message:124:7", world.now)))
        assertEquals(1, world.display.notices.size)
    }

    @Test fun receipt_fails_closed_for_every_account_identity_and_without_registration_or_permission() = NotificationFixture().use { world ->
        world.register()
        val good = world.notice()
        listOf(good.copy(recipientId = 8), good.copy(installationId = "other"), good.copy(bindingId = "other"),
            good.copy(conversationId = 0), good.copy(eventId = ""), good.copy(title = ""), good.copy(body = "")).forEach {
            assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(it))
        }
        world.display.enabled = false
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(good))
        world.display.enabled = true
        world.store.value = world.store.value.copy(binding = world.store.value.binding!!.copy(acknowledged = false))
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(good.copy(eventId = "message:125:7")))
        world.sessions.clearSession()
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(good))
        assertTrue(world.display.notices.isEmpty())
    }

    @Test fun current_chat_push_refreshes_authoritative_data_preserving_draft_and_reading_position() = NotificationFixture().use { world ->
        world.register()
        world.chat.open()
        world.chat.conversation.onPromptChange("reply in progress")
        val reading = ConversationReadingPosition()
        reading.onItems(world.chat.ready().items, atBottom = false, scrolling = false)
        world.chat.conversation.onNotificationVisibilityChanged(true)
        world.chat.missMessages()
        val calls = world.chat.repository.detailCalls
        assertEquals(ReceiptOutcome.Suppressed, world.notifications.receive(world.notice()))
        world.chat.scheduler.advanceUntilIdle()
        assertEquals(calls + 1, world.chat.repository.detailCalls)
        assertEquals(listOf(1, 2, 3, 4), world.chat.serverMessages().map { it.id })
        assertEquals("reply in progress", world.chat.ready().promptInput)
        assertFalse(reading.onItems(world.chat.ready().items, atBottom = false, scrolling = false))
        assertTrue(reading.hasNewMessage)
        assertTrue(world.display.notices.isEmpty())
        assertEquals(ReceiptOutcome.Duplicate, world.notifications.receive(world.notice()))
        world.chat.conversation.onNotificationVisibilityChanged(false)
        assertEquals(ReceiptOutcome.Posted, world.notifications.receive(world.notice("message:126:7")))
    }

    @Test fun one_use_taps_require_local_capability_active_binding_and_unexpired_target() = NotificationFixture().use { world ->
        world.register()
        world.notifications.receive(world.notice())
        val tap = world.display.notices.single().second
        world.notifications.acceptTap("forged")
        assertNull(world.notifications.consumeTarget())
        world.notifications.acceptTap(tap)
        assertEquals(42, world.notifications.consumeTarget())
        world.notifications.acceptTap(tap)
        assertNull(world.notifications.consumeTarget())
        world.notifications.receive(world.notice("message:124:7"))
        world.notifications.acceptTap(world.display.notices.last().second)
        world.local.invalidate()
        assertNull(world.notifications.consumeTarget())
    }

    @Test fun logout_invalidates_before_bounded_delete_and_clears_credentials_even_offline() = NotificationFixture().use { world ->
        world.register()
        val notice = world.notice()
        world.notifications.receive(notice)
        val tap = world.display.notices.single().second
        world.repository.removalGate = CompletableDeferred()
        world.repository.onRemove = { oldSession ->
            assertEquals(oldSession, world.sessions.getSession())
            assertTrue(world.display.notices.isEmpty())
            assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(notice))
            world.notifications.acceptTap(tap)
            assertNull(world.notifications.consumeTarget())
        }
        world.logout()
        assertNull(world.sessions.getSession())
        assertEquals(1, world.repository.removed.size)
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(notice))
    }

    @Test fun replacement_session_is_never_cleared_by_old_logout_callback() = NotificationFixture().use { world ->
        world.register()
        val replacement = AuthSession(User("new-provider", "new@example.test"), "new-token")
        world.repository.onRemove = { world.sessions.saveSession(replacement) }
        world.logout()
        assertEquals(replacement, world.sessions.getSession())
    }

    @Test fun denial_is_requested_once_and_missing_firebase_does_not_remove_session() = NotificationFixture().use { world ->
        assertTrue(world.notifications.requestPermissionOnce())
        assertFalse(world.notifications.requestPermissionOnce())
        val session = world.sessions.getSession()
        world.token = null
        world.register()
        assertEquals(session, world.sessions.getSession())
        assertTrue(world.repository.registered.isEmpty())
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(world.notice()))
    }

    @Test fun failed_durable_dedup_write_cannot_post_or_issue_a_tap() = NotificationFixture().use { world ->
        world.register()
        world.store.writable = false
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(world.notice()))
        assertTrue(world.display.notices.isEmpty())
    }

    @Test fun logout_at_final_posting_check_prevents_native_notice_and_pending_tap() = NotificationFixture().use { world ->
        world.register()
        world.display.onCanPost = {
            world.local.invalidate()
            world.sessions.clearSession()
        }
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(world.notice()))
        assertTrue(world.display.notices.isEmpty())
        assertTrue(world.store.read().handled.isEmpty())
        assertNull(world.notifications.consumeTarget())
    }
}
