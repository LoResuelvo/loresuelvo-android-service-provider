package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.loresuelvo.serviceprovider.R

@Composable
internal fun ProviderEvidenceViewer(imageUrl: String, imageName: String, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("provider_evidence_viewer")
        .semantics { contentDescription = imageName }) {
        SubcomposeAsyncImage(model = imageUrl, contentDescription = imageName,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            loading = { CircularProgressIndicator(color = Color.White) },
            error = {
                Icon(Icons.Filled.BrokenImage,
                    contentDescription = stringResource(R.string.provider_order_evidence_photo_error),
                    tint = Color.White)
            })
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)
            .padding(16.dp).testTag("provider_evidence_close")) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.provider_order_evidence_close),
                tint = Color.White)
        }
    }
}
