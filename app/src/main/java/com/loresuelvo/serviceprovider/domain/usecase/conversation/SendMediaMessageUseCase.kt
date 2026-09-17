package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import javax.inject.Inject

/**
 * Sends a provider-typed media message (image in US-B; audio
 * lands in US-C on top) to an existing conversation.
 *
 * Single seam the ViewModel uses to fire `POST
 * /conversations/{id}/messages` with `image_file_ids[]` /
 * `audio_file_id` — future attachment flows (US-C audio) land on
 * the same port via parallel use cases rather than overloading
 * this one.
 *
 *  - Rejects an empty payload with a typed `Server` outcome so
 *    the backend never sees a meaningless request — catches the
 *    corner case where the picker returns a URI but the
 *    `ContentResolver` can't open an `InputStream` (revoked
 *    permission, deleted file, misconfigured file provider).
 *  - Forwards a non-empty payload verbatim to the backend. The
 *    server's `created_on`, id, and confirmed media URLs are
 *    surfaced back through [SendMessageOutcome.Success.message]
 *    so the ViewModel can replace the optimistic bubble.
 *
 * Pure orchestration. No Android, no coroutine scope of its own.
 */
class SendMediaMessageUseCase @Inject constructor(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(
        conversationId: Int,
        media: List<MediaUpload>,
    ): SendMessageOutcome {
        require(conversationId > 0) { "conversationId must be positive" }
        if (media.isEmpty() || media.all { it.bytes.isEmpty() }) {
            return SendMessageOutcome.Failure.Server(
                code = 0,
                message = "Media payload is empty",
            )
        }
        return repository.sendMediaMessage(conversationId, media)
    }
}
