package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar
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
    onUnavailable: () -> Unit = onClose,
    onRetry: () -> Unit,
    onAccept: () -> Unit = {},
    onRetryAccept: () -> Unit = {},
    selectedImageIndex: Int? = null,
    onImageSelected: (Int) -> Unit = {},
    onImageViewerClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val accepting = uiState is JobRequestDetailUiState.Accepting
    val request = when (uiState) {
        is JobRequestDetailUiState.Ready -> uiState.request
        is JobRequestDetailUiState.Accepting -> uiState.request
        is JobRequestDetailUiState.AcceptError -> uiState.request
        else -> null
    }
    val dismiss = { if (!accepting) onClose() }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
            confirmValueChange = { !accepting || it != SheetValue.Hidden }),
    ) {
        Column(modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.provider_job_request_detail_title),
                    Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = dismiss, enabled = !accepting) {
                    Icon(Icons.Default.Close, stringResource(R.string.provider_job_request_detail_close))
                }
            }
            when (uiState) {
                JobRequestDetailUiState.Loading -> LoadingContent()
                JobRequestDetailUiState.NotFound -> MessageContent(
                    stringResource(R.string.provider_job_request_detail_unavailable),
                    stringResource(R.string.provider_job_request_detail_close), onUnavailable)
                JobRequestDetailUiState.Error -> MessageContent(
                    stringResource(R.string.provider_job_request_detail_error),
                    stringResource(R.string.provider_job_request_detail_retry), onRetry)
                is JobRequestDetailUiState.AcceptUnavailable -> MessageContent(
                    stringResource(R.string.provider_job_request_detail_unavailable),
                    stringResource(R.string.provider_job_request_detail_close), onUnavailable)
                else -> if (request != null) {
                    DetailContent(request, onImageSelected,
                        Modifier.weight(1f, fill = false))
                    RequestChatAction(accepting, uiState is JobRequestDetailUiState.AcceptError,
                        onAccept, onRetryAccept)
                }
            }
        }
        request?.images?.getOrNull(selectedImageIndex ?: -1)?.let {
            JobRequestImageViewer(it, onImageViewerClose)
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
            .fillMaxWidth()
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
    onImageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProviderAvatar(request.consumerName, "", null,
                stringResource(R.string.proposal_list_avatar, request.consumerName), size = 48.dp)
            Text(request.consumerName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        }
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
    }
}

@Composable
private fun RequestChatAction(
    accepting: Boolean,
    acceptError: Boolean,
    onAccept: () -> Unit,
    onRetryAccept: () -> Unit,
) {
    Surface(shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.job_request_chat_explanation),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Button(
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
