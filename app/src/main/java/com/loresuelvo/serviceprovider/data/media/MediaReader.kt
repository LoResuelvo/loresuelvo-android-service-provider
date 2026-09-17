package com.loresuelvo.serviceprovider.data.media

import android.net.Uri
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload

/**
 * Port that converts a `content://` / `file://` URI handed by
 * an Android picker or camera capture into a domain
 * [MediaUpload]. Keeps the
 * [com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationViewModel]
 * free of `android.content.ContentResolver` so the JVM unit tests
 * can swap a fake and assert the resulting media state without
 * spinning up Robolectric.
 *
 * The reader never swallows a missing / unreadable URI:
 * unrecoverable I/O errors bubble up as [java.io.IOException] so
 * the calling layer (the VM) can translate them into a typed
 * failure for the UI.
 *
 * The implementation lives in [AndroidMediaReader] — the only
 * place in the codebase that touches `ContentResolver` /
 * `OpenableColumns`.
 */
interface MediaReader {

    /**
     * Reads the bytes / mime / display name of the file the URI
     * points at and packages them as a polymorphic [MediaUpload].
     * The polymorphic shape lets a future audio scenario (US-C)
     * land without changing the VM call site; the reader inspects
     * the URI's mime type and dispatches.
     *
     * @throws java.io.IOException when the URI cannot be opened
     *  (revoked permission, missing provider, deleted file).
     */
    suspend fun read(uri: Uri): MediaUpload
}
