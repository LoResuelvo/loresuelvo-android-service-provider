package com.loresuelvo.serviceprovider.domain.usecase.conversation

import com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository
import com.loresuelvo.serviceprovider.domain.conversation.MAX_AUDIO_BYTES
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome
import javax.inject.Inject

/**
 * Sends a provider-typed media message (image in US-B; audio in
 * US-C) to an existing conversation.
 *
 * Single seam the ViewModel uses to fire `POST
 * /conversations/{id}/messages` with `image_file_ids[]` /
 * `audio_file_id` — the upload orchestration (presign → upload →
 * confirm → post) lives in the data layer so the use case stays
 * a thin pass-through.
 *
 *  - Rejects an empty payload with a typed `Server` outcome so
 *    the backend never sees a meaningless request — catches the
 *    corner case where the picker returns a URI but the
 *    `ContentResolver` can't open an `InputStream` (revoked
 *    permission, deleted file, misconfigured file provider).
 *  - Rejects audio clips larger than [MAX_AUDIO_BYTES] (10 MB)
 *    with a typed [SendMessageOutcome.Failure.PayloadTooLarge]
 *    so the backend never sees the request — saves a wasted
 *    round-trip and gives the UI a clearer copy than a generic
 *    `Server(413)`. Image uploads don't need this guard — the
 *    picker caps the dimensions before they reach the use case.
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
        media.forEach { attachment ->
            if (attachment is MediaUpload.Audio &&
                attachment.bytes.size.toLong() > MAX_AUDIO_BYTES
            ) {
                return SendMessageOutcome.Failure.PayloadTooLarge(
                    maxBytes = MAX_AUDIO_BYTES,
                )
            }
        }
        return repository.sendMediaMessage(conversationId, media)
    }
}
