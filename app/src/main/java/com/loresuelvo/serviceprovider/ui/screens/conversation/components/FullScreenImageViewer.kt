package com.loresuelvo.serviceprovider.ui.screens.conversation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.loresuelvo.serviceprovider.R

/** Dialog back closes only the viewer; accessible controls complement pinch zoom. */
@Composable
internal fun FullScreenImageViewer(url: String, name: String, onClose: () -> Unit) {
    var scale by rememberSaveable(url) { mutableFloatStateOf(1f) }
    var offsetX by rememberSaveable(url) { mutableFloatStateOf(0f) }
    var offsetY by rememberSaveable(url) { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding().testTag("provider-image-viewer")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.provider_conversation_close))
                }
                TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.provider_image_reset))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().pointerInput(url) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX = if (scale > 1f) offsetX + pan.x else 0f
                    offsetY = if (scale > 1f) offsetY + pan.y else 0f
                }
            }, contentAlignment = Alignment.Center) {
                AsyncImage(model = url, contentDescription = name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
                    })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { scale = (scale - 1f).coerceAtLeast(1f); if (scale == 1f) { offsetX = 0f; offsetY = 0f } }, enabled = scale > 1f,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("provider-image-zoom-out")) {
                    Text(stringResource(R.string.provider_image_zoom_out))
                }
                TextButton(onClick = { scale = (scale + 1f).coerceAtMost(5f) }, enabled = scale < 5f,
                    modifier = Modifier.heightIn(min = 48.dp).testTag("provider-image-zoom-in")) {
                    Text(stringResource(R.string.provider_image_zoom_in))
                }
            }
        }
    }
}
