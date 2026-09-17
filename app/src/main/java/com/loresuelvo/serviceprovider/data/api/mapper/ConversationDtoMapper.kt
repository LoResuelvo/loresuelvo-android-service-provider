package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.ConversationCounterpartDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationDetailDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationMessageDto
import com.loresuelvo.serviceprovider.data.api.dto.MessageImageDto
import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessageKind
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.conversation.MediaReference

internal fun ConversationDto.toDomain(): Conversation = Conversation(
    id = id.requirePositive("conversation id"),
    status = status.toConversationStatus(),
    counterpart = counterpart.toDomain(),
    lastMessage = lastMessage?.toDomain(),
    updatedOnEpochMillis = updatedOn?.toEpochMillis()
        ?: throw IllegalArgumentException("Missing conversation updated_on"),
)

internal fun ConversationDetailDto.toDomain(): ConversationDetail = ConversationDetail(
    id = id.requirePositive("conversation id"),
    status = status.toConversationStatus(),
    counterpart = (work?.counterpart ?: counterpart)
        ?.toDomain()
        ?: throw IllegalArgumentException(
            "Missing conversation counterpart (neither work.counterpart nor root counterpart)",
        ),
    messages = messages.map { it.toDomain() },
    updatedOnEpochMillis = updatedOn?.toEpochMillis()
        ?: throw IllegalArgumentException("Missing conversation updated_on"),
)

internal fun ConversationCounterpartDto.toDomain(): ConversationCounterpart {
    require(role.isNullOrBlank() || role.equals("consumer", ignoreCase = true)) {
        "Conversation counterpart is not a consumer"
    }
    return ConversationCounterpart(
        id = id.requirePositive("counterpart id"),
        name = name.requireNotBlank("counterpart name"),
        surname = surname.requireNotBlank("counterpart surname"),
        profilePhotoUrl = profilePhotoUrl?.takeIf(String::isNotBlank),
    )
}

internal fun ConversationMessageDto.toDomain(): ConversationMessage {
    val messageId = id.requirePositive("message id")
    val createdOnMillis = createdOn?.toEpochMillis()
        ?: throw IllegalArgumentException("Missing message created_on")
    val kind = when {
        audio != null -> ConversationMessageKind.Audio
        video != null -> ConversationMessageKind.Video
        else -> ConversationMessageKind.Text
    }
    return ConversationMessage(
        id = messageId,
        sender = senderRole.toConversationSender(messageId),
        content = content,
        createdOnEpochMillis = createdOnMillis,
        kind = kind,
        media = images?.firstOrNull()?.toMediaReference(),
    )
}

private fun MessageImageDto.toMediaReference(): MediaReference.Image = MediaReference.Image(
    id = id,
    url = url,
    mimeType = mimeType,
    originalName = originalName,
)

private fun String.toConversationSender(messageId: Int): ConversationSender = when (lowercase()) {
    "consumer" -> ConversationSender.Consumer
    "provider" -> ConversationSender.Provider
    else -> throw IllegalArgumentException(
        "Unsupported sender_role '$this' for message $messageId",
    )
}

internal fun String.toConversationStatus(): ConversationStatus = when (lowercase()) {
    "pending" -> ConversationStatus.Pending
    "active" -> ConversationStatus.Active
    "rejected" -> ConversationStatus.Rejected
    else -> ConversationStatus.Unsupported(this)
}

private fun Int.requirePositive(label: String): Int =
    also { require(it > 0) { "$label must be positive" } }

private fun String.requireNotBlank(label: String): String =
    also { require(isNotBlank()) { "$label must not be blank" } }
