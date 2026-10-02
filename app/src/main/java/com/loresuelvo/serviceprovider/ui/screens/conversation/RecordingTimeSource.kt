package com.loresuelvo.serviceprovider.ui.screens.conversation

import javax.inject.Inject

/** Monotonic clock; tests replace it with their coroutine scheduler. */
open class RecordingTimeSource @Inject constructor() {
    open fun nowMillis(): Long = android.os.SystemClock.elapsedRealtime()
}
