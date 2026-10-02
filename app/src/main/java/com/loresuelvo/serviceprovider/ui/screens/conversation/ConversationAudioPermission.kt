package com.loresuelvo.serviceprovider.ui.screens.conversation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Bind delayed permission results to the originating, foreground conversation. */
@Composable
internal fun ConversationAudioPermission(
    conversationId: Int,
    canRecord: () -> Boolean,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
    content: @Composable (() -> Unit) -> Unit,
) {
    var origin by rememberSaveable { mutableStateOf<Int?>(null) }
    val currentId by rememberUpdatedState(conversationId)
    val currentCanRecord by rememberUpdatedState(canRecord)
    val granted by rememberUpdatedState(onGranted)
    val denied by rememberUpdatedState(onDenied)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val sameConversation = origin == currentId
        origin = null
        if (sameConversation && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && currentCanRecord()) {
            if (allowed) granted() else denied()
        }
    }
    content {
        if (origin == null && currentCanRecord()) {
            origin = currentId
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
}

/** Capture and playback never continue after this conversation leaves the foreground. */
@Composable
internal fun ConversationAudioLifecycle(onBackground: () -> Unit) {
    val callback by rememberUpdatedState(onBackground)
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_STOP) { callback() }
    DisposableEffect(Unit) { onDispose { callback() } }
}
