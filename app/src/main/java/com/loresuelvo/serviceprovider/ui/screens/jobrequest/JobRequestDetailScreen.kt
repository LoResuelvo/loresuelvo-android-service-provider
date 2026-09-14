package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestImage
import com.loresuelvo.serviceprovider.ui.jobrequest.JobRequestDetailUiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JobRequestDetailScreen(
    uiState: JobRequestDetailUiState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onAccept: () -> Unit = {},
    onRetryAccept: () -> Unit = {},
    selectedImageIndex: Int? = null,
    onImageSelected: (Int) -> Unit = {},
    onImageViewerClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_job_request_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.provider_job_request_detail_close),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        when (uiState) {
            JobRequestDetailUiState.Loading -> LoadingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
            JobRequestDetailUiState.NotFound -> MessageContent(
                message = stringResource(R.string.provider_job_request_detail_unavailable),
                actionLabel = stringResource(R.string.provider_job_request_detail_close),
                onAction = onClose,
                modifier = Modifier.padding(contentPadding),
            )
            JobRequestDetailUiState.Error -> MessageContent(
                message = stringResource(R.string.provider_job_request_detail_error),
                actionLabel = stringResource(R.string.provider_job_request_detail_retry),
                onAction = onRetry,
                modifier = Modifier.padding(contentPadding),
            )
            is JobRequestDetailUiState.Ready -> DetailContent(
                request = uiState.request,
                onAccept = onAccept,
                onImageSelected = onImageSelected,
                modifier = Modifier.padding(contentPadding),
            )
            is JobRequestDetailUiState.Accepting -> DetailContent(
                request = uiState.request,
                accepting = true,
                onImageSelected = onImageSelected,
                modifier = Modifier.padding(contentPadding),
            )
            is JobRequestDetailUiState.AcceptError -> DetailContent(
                request = uiState.request,
                acceptError = true,
                onRetryAccept = onRetryAccept,
                onImageSelected = onImageSelected,
                modifier = Modifier.padding(contentPadding),
            )
        }

        val selectedImage = (uiState as? JobRequestDetailUiState.Ready)
            ?.request
            ?.images
            ?.getOrNull(selectedImageIndex ?: -1)
        if (selectedImage != null) {
            JobRequestImageViewer(
                image = selectedImage,
                onClose = onImageViewerClose,
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun MessageContent(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun DetailContent(
    request: JobRequest,
    onAccept: () -> Unit = {},
    onRetryAccept: () -> Unit = {},
    accepting: Boolean = false,
    acceptError: Boolean = false,
    onImageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = request.consumerName,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = request.title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = request.description,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        if (request.images.isNotEmpty()) {
            Text(
                text = stringResource(R.string.provider_job_request_images_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                request.images.forEachIndexed { index, image ->
                    AsyncImage(
                        model = image.url,
                        contentDescription = stringResource(
                            R.string.provider_job_request_image_description,
                            image.originalName,
                        ),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clickable { onImageSelected(index) },
                    )
                }
            }
        }
        if (acceptError) {
            Text(
                text = stringResource(R.string.provider_job_request_accept_error),
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = onRetryAccept,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.provider_job_request_detail_retry))
            }
        } else {
            OutlinedButton(
                onClick = onAccept,
                enabled = !accepting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (accepting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(stringResource(R.string.provider_job_request_continue_conversation))
            }
        }
        OutlinedButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.provider_job_request_reject))
        }
    }
}

@Composable
private fun JobRequestImageViewer(
    image: JobRequestImage,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = image.url,
                    contentDescription = stringResource(
                        R.string.provider_job_request_image_description,
                        image.originalName,
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(
                            R.string.provider_job_request_image_viewer_close,
                        ),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}
