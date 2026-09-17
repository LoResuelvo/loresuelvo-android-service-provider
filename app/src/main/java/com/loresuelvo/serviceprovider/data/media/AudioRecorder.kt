package com.loresuelvo.serviceprovider.data.media

import android.net.Uri

/**
 * Port for recording audio from the device microphone.
 *
 * The implementation owns the temporary recording file and
 * returns its [Uri] when recording successfully stops.
 *
 * Permission handling (`RECORD_AUDIO`) belongs to the UI layer:
 * the route acquires the runtime permission via
 * `ActivityResultContracts.RequestPermission` and only invokes
 * [start] when the user has already granted the permission.
 */
interface AudioRecorder {

    /**
     * Starts a new recording.
     *
     * @return `Result.success(Unit)` when recording starts, or
     *  `Result.failure(throwable)` when the recorder can't be
     *  started (e.g. another recording is already in progress).
     */
    fun start(): Result<Unit>

    /**
     * Stops the current recording.
     *
     * @return the URI of the recorded clip on success; a typed
     *  failure if the recorder wasn't started.
     */
    fun stop(): Result<Uri>

    /**
     * Cancels the current recording and removes the temporary
     * file. Safe to call when no recording is in flight (no-op).
     */
    fun cancel()
}
