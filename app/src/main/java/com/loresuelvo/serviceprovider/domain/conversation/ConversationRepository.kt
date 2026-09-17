package com.loresuelvo.serviceprovider.domain.conversation

import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome

/**
 * Port for the provider ↔ consumer conversations backend.
 * Implementations live in `data/api/` and translate the wire
 * contract (snake_case JSON over the Retrofit-typed
 * [com.loresuelvo.serviceprovider.data.api.BackendApi]) into the
 * domain's [ConversationsOutcome] / [ConversationDetailOutcome] /
 * [SendMessageOutcome] hierarchies.
 *
 * The repository never throws on HTTP / network failures: every
 * exception is mapped to a typed `Failure` (exhaustive `when`
 * against [com.loresuelvo.serviceprovider.domain.api.ApiError]).
 * Implementations must remain pure with respect to UI concerns —
 * no Android, no kotlinx-serialization, no Hilt.
 */
interface ConversationRepository {

    /**
     * Lists every conversation the authenticated provider has
     * open, ordered by the backend's `updated_on` descending
     * (most recent first). Each entry includes the counterpart
     * (consumer) profile and the last message preview.
     *
     * Empty list ⇒ the provider has not opened or been invited to
     * any conversation yet; the UI renders an "empty state" CTA.
     */
    suspend fun getConversations(): ConversationsOutcome

    /**
     * Loads the full snapshot of a single conversation, including
     * the complete ordered message thread. The chat surface uses
     * it on entry to render the header (counterpart, status) and
     * the existing bubbles. Empty `messages` is a valid response
     * for a brand-new conversation that was just opened by the
     * consumer — the chat shows the empty body + an enabled
     * input bar.
     */
    suspend fun getConversationById(
        conversationId: Int,
    ): ConversationDetailOutcome

    /**
     * Appends a provider-typed text message to the given
     * conversation. The [content] is sent verbatim to the
     * backend; trimming / blank-guarding is the use case's
     * responsibility.
     *
     * On success the carried [ConversationMessage] is the
     * server-persisted bubble (with the backend's stable id and
     * authoritative timestamp). The VM uses it to replace the
     * optimistic bubble it appended locally.
     *
     * 404 on `conversationId` (the consumer dropped the thread)
     * maps to [SendMessageOutcome.Failure.ConversationNotFound].
     */
    suspend fun sendMessage(
        conversationId: Int,
        content: String,
    ): SendMessageOutcome

    /**
     * Appends a provider-typed media message (image in US-B; audio
     * lands in US-C on top) to the given conversation. The list
     * is homogeneous — the repository dispatches on the first
     * entry's runtime type and rejects mixed lists with a typed
     * `Server` failure so the UI surfaces a clear error rather
     * than silently dropping the rest.
     *
     * Implementations orchestrate the presign → upload → confirm
     * pipeline (delegating to `FileRepository`) and then post the
     * JSON message body with the joined file ids. The wire
     * contract is JSON, not multipart: the backend does not
     * accept attachments on the conversations endpoint
     * directly.
     *
     * 404 on `conversationId` maps to
     * [SendMessageOutcome.Failure.ConversationNotFound]. The
     * default implementation throws so the port can be extended
     * incrementally (US-B only requires images).
     */
    suspend fun sendMediaMessage(
        conversationId: Int,
        media: List<MediaUpload>,
    ): SendMessageOutcome =
        throw UnsupportedOperationException(
            "sendMediaMessage is not implemented by this ConversationRepository",
        )
}
