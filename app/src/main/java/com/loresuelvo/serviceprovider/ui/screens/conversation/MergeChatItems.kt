package com.loresuelvo.serviceprovider.ui.screens.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.mergeConversationMessages

internal fun mergeChatItems(existing: List<ChatListItem>, incoming: List<ConversationMessage>): List<ChatListItem> {
    val confirmed = mergeConversationMessages(existing.filterIsInstance<ChatListItem.ServerConfirmed>().map { it.message }, incoming)
    return (confirmed.map(ChatListItem::ServerConfirmed) + existing.filter { it !is ChatListItem.ServerConfirmed })
        .sortedWith(compareBy<ChatListItem> { it.createdOnEpochMillis }.thenBy { (it as? ChatListItem.ServerConfirmed)?.message?.id ?: Int.MAX_VALUE }.thenBy { it.key })
}

internal fun conversationReadyState(
    detail: ConversationDetail,
    previous: ProviderConversationUiState.Ready?,
    buffered: List<ConversationMessage>,
): ProviderConversationUiState.Ready {
    val items = mergeChatItems(previous?.items.orEmpty(), detail.messages + buffered)
    val mergedDetail = detail.copy(messages = items.filterIsInstance<ChatListItem.ServerConfirmed>().map { it.message })
    return previous?.copy(detail = mergedDetail, items = items, refreshing = false, refreshFailure = null)
        ?: ProviderConversationUiState.Ready(mergedDetail, items, promptInput = "", sending = false)
}
