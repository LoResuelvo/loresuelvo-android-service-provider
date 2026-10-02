package com.loresuelvo.serviceprovider.ui.screens.conversation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.loresuelvo.serviceprovider.domain.conversation.CameraOutput

/** Save camera output with its origin so restored external results cannot attach to another chat. */
@Composable
internal fun ConversationImageLaunchers(
    conversationId: Int,
    output: CameraOutput,
    canStart: () -> Boolean,
    onImages: (List<String>, Int?) -> Unit,
    content: @Composable (gallery: () -> Unit, camera: () -> Unit, replace: (Int) -> Unit) -> Unit,
) {
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraOrigin by rememberSaveable { mutableStateOf<Int?>(null) }
    var galleryOrigin by rememberSaveable { mutableStateOf<Int?>(null) }
    var replacement by rememberSaveable { mutableStateOf<Int?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { uris ->
        if (galleryOrigin == conversationId && canStart() && uris.isNotEmpty()) {
            onImages(uris.map(Uri::toString), replacement)
        }
        galleryOrigin = null
        replacement = null
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraUri
        if (success && cameraOrigin == conversationId && uri != null && canStart()) onImages(listOf(uri), null)
        cameraUri = null
        cameraOrigin = null
    }
    fun pick(index: Int?) {
        if (!canStart() || galleryOrigin != null || cameraOrigin != null) return
        galleryOrigin = conversationId
        replacement = index
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    content({ pick(null) }, {
        if (canStart() && galleryOrigin == null && cameraOrigin == null) {
            cameraOrigin = conversationId
            cameraUri = output.createCameraOutputUri()
            camera.launch(Uri.parse(cameraUri))
        }
    }, { pick(it) })
}
