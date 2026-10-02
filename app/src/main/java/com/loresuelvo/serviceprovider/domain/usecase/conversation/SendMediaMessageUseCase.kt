package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import com.loresuelvo.serviceprovider.domain.conversation.validateMediaUploads
import javax.inject.Inject

class SendMediaMessageUseCase @Inject constructor(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(conversationId: Int, media: List<MediaUpload>): SendMessageOutcome {
        require(conversationId > 0) { "conversationId must be positive" }
        return validateMediaUploads(media) ?: repository.sendMediaMessage(conversationId, media)
    }
}
