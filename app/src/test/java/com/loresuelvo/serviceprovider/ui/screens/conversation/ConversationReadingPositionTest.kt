package com.loresuelvo.serviceprovider.ui.screens.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import org.junit.Assert.*
import org.junit.Test

class ConversationReadingPositionTest {
    private fun message(id: Int) = ChatListItem.ServerConfirmed(
        ConversationMessage(id, ConversationSender.Consumer, "message-$id", id.toLong()),
    )

    @Test fun arrivals_follow_only_a_settled_bottom_and_ignore_duplicate_or_reordered_items() {
        val reading = ConversationReadingPosition()
        assertTrue(reading.onItems(listOf(message(1)), false, false))
        assertTrue(reading.onItems(listOf(message(1), message(2)), true, false))
        assertFalse(reading.onItems(listOf(message(2), message(1)), true, false))
        assertFalse(reading.onItems(listOf(message(1), message(2), message(3)), true, true))
        assertTrue(reading.hasNewMessage)
        reading.reachedBottom()
        assertFalse(reading.hasNewMessage)
    }

    @Test fun history_collects_one_notice_and_an_own_send_intentionally_follows() {
        val reading = ConversationReadingPosition()
        reading.onItems(listOf(message(1)), true, false)
        assertFalse(reading.onItems(listOf(message(1), message(2)), false, false))
        assertFalse(reading.onItems(listOf(message(1), message(2), message(3)), false, false))
        assertTrue(reading.hasNewMessage)
        val pending = ChatListItem.LocalPending("own", ConversationSender.Provider, "reply", 4)
        assertTrue(reading.onItems(listOf(message(1), message(2), message(3), pending), false, false))
        assertFalse(reading.hasNewMessage)
    }
}
