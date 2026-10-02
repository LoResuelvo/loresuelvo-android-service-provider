package com.loresuelvo.serviceprovider.ui.screens.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender

/** Owns reading intent; the list keeps its stable-key anchor when following is declined. */
internal class ConversationReadingPosition {
    private var knownKeys: Set<String>? = null
    var hasNewMessage = false
        private set

    fun onItems(items: List<ChatListItem>, atBottom: Boolean, scrolling: Boolean): Boolean {
        val previous = knownKeys
        knownKeys = items.map { it.key }.toSet()
        val added = items.filter { previous != null && it.key !in previous }
        val ownSend = added.any { it is ChatListItem.LocalPending }
        val incoming = added.any { it.sender == ConversationSender.Consumer }
        val follow = previous == null || ownSend || (incoming && atBottom && !scrolling)
        if (follow) hasNewMessage = false else if (incoming) hasNewMessage = true
        return follow && items.isNotEmpty()
    }

    fun selectNewMessages(items: List<ChatListItem>): Int? {
        hasNewMessage = false
        return items.lastIndex.takeIf { it >= 0 }
    }

    fun reachedBottom() { hasNewMessage = false }
}
