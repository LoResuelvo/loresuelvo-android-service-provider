package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.ui.turns.CompletionEvidenceSelection
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionIssue
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionStatus
import com.loresuelvo.serviceprovider.ui.turns.EvidenceUploadStatus
import com.loresuelvo.serviceprovider.ui.turns.CompletionSubmissionState
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionUploadStage
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionDraftValidation
import coil3.compose.AsyncImage
import java.io.File

enum class CompletionFormAvailability {
    Checking, Eligible, TooEarly, AlreadyReported, Forbidden, ChangedOrder, Unavailable, NotFound, Error,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProviderCompletionFormScreen(
    order: WorkOrder,
    availability: CompletionFormAvailability,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onAddPhotos: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    canAddPhotos: Boolean = false,
    evidence: List<CompletionEvidenceSelection> = emptyList(),
    evidenceIssue: EvidenceSelectionIssue? = null,
    onRemoveEvidence: (Long) -> Unit = {},
    onRetryEvidence: (Long) -> Unit = {},
    validationIssue: CompletionDraftValidation.Invalid? = null,
    onSubmitAttempt: () -> Unit = {},
    canAttemptSubmit: Boolean = false,
    submission: CompletionSubmissionState = CompletionSubmissionState.Idle,
    onRetryReconciliation: () -> Unit = {},
    refreshedOrderStatus: WorkOrderStatus? = null,
) {
    val editable = submission == CompletionSubmissionState.Idle
    Scaffold(topBar = { TopAppBar(
        title = { Text(stringResource(R.string.provider_completion_title)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.provider_turns_back)) } },
    ) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(order.consumerName, style = MaterialTheme.typography.titleLarge)
            Text(order.description, style = MaterialTheme.typography.bodyLarge)
            when (submission) {
                is CompletionSubmissionState.Confirmed -> {
                    Text(stringResource(R.string.provider_completion_report_success))
                    val statusLabel = if (submission.serverConfirmed)
                        refreshedOrderStatus?.let { providerTurnStatusBadge(it)?.label } else null
                    if (statusLabel != null) {
                        Text(stringResource(statusLabel))
                    } else {
                        Text(stringResource(R.string.provider_completion_report_refresh_pending))
                        Button(onClick = onRetryReconciliation,
                            modifier = Modifier.testTag("completion_retry_query")) {
                            Text(stringResource(R.string.provider_completion_report_retry_query))
                        }
                    }
                }
                CompletionSubmissionState.QueryFailed -> {
                    Text(stringResource(R.string.provider_completion_report_query_failed))
                    Button(onClick = onRetryReconciliation,
                        modifier = Modifier.testTag("completion_retry_query")) {
                        Text(stringResource(R.string.provider_completion_report_retry_query))
                    }
                }
                CompletionSubmissionState.Reconciling -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.provider_completion_report_reconciling))
                }
                else -> when (availability) {
                CompletionFormAvailability.Checking -> {
                    CircularProgressIndicator(Modifier.testTag("completion_checking"))
                    Text(stringResource(R.string.provider_completion_checking))
                }
                CompletionFormAvailability.TooEarly -> Text(stringResource(R.string.provider_completion_too_early))
                CompletionFormAvailability.AlreadyReported -> Text(stringResource(R.string.provider_completion_already_reported))
                CompletionFormAvailability.Forbidden -> Text(stringResource(R.string.provider_completion_forbidden))
                CompletionFormAvailability.ChangedOrder,
                CompletionFormAvailability.Unavailable,
                CompletionFormAvailability.NotFound,
                CompletionFormAvailability.Error -> {
                    Text(stringResource(when (availability) {
                        CompletionFormAvailability.ChangedOrder -> R.string.provider_completion_changed
                        CompletionFormAvailability.Unavailable -> R.string.provider_completion_unavailable
                        CompletionFormAvailability.NotFound -> R.string.provider_completion_missing
                        else -> R.string.provider_completion_error
                    }))
                    Button(onClick = onRetry) { Text(stringResource(R.string.provider_home_retry)) }
                }
                CompletionFormAvailability.Eligible -> {
                    when (submission) {
                        CompletionSubmissionState.Checking -> Text(stringResource(R.string.provider_completion_report_checking))
                        CompletionSubmissionState.Sending -> Text(stringResource(R.string.provider_completion_report_sending))
                        else -> Unit
                    }
                    OutlinedTextField(value = description, onValueChange = onDescriptionChange,
                        label = { Text(stringResource(R.string.provider_completion_description)) },
                        modifier = Modifier.fillMaxWidth().testTag("completion_description"), minLines = 3,
                        enabled = editable)
                    Text(stringResource(R.string.provider_completion_photos), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.provider_completion_photo_limit),
                        style = MaterialTheme.typography.bodySmall)
                    if (evidence.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            evidence.forEachIndexed { index, selection ->
                                CompletionEvidenceItem(index, selection, onRemoveEvidence, onRetryEvidence, editable)
                            }
                        }
                    }
                    if (evidenceIssue != null) {
                        Text(stringResource(when (evidenceIssue) {
                            EvidenceSelectionIssue.MaximumReached -> R.string.provider_completion_photo_maximum
                            EvidenceSelectionIssue.AlreadySelected -> R.string.provider_completion_photo_duplicate
                        }), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = onAddPhotos,
                        enabled = canAddPhotos && editable) {
                        Text(stringResource(R.string.provider_completion_add_photos))
                    }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.provider_completion_cancel)) }
                    if (validationIssue != null) {
                        Text(stringResource(validationIssue.messageResource()),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("completion_validation_issue"))
                    }
                    Button(onClick = onSubmitAttempt,
                        enabled = canAttemptSubmit && editable,
                        modifier = Modifier.testTag("completion_submit")) {
                        Text(stringResource(R.string.provider_completion_submit))
                    }
                }
                }
            }
        }
    }
}

private fun CompletionDraftValidation.Invalid.messageResource(): Int = when (this) {
    CompletionDraftValidation.Invalid.DescriptionRequired -> R.string.provider_completion_description_required
    CompletionDraftValidation.Invalid.PhotoRequired -> R.string.provider_completion_photo_required
    CompletionDraftValidation.Invalid.TooManyPhotos -> R.string.provider_completion_photo_maximum
    CompletionDraftValidation.Invalid.UnconfirmedPhoto -> R.string.provider_completion_photo_unconfirmed
    CompletionDraftValidation.Invalid.DuplicatePhotoIds -> R.string.provider_completion_photo_duplicate_ids
}

@Composable
private fun CompletionEvidenceItem(
    index: Int,
    selection: CompletionEvidenceSelection,
    onRemove: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    editable: Boolean,
) {
    Column(Modifier.width(96.dp).testTag("completion_evidence_${index}_${selection.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            when (val status = selection.status) {
                is EvidenceSelectionStatus.Ready -> AsyncImage(
                    model = File(status.image.localPath),
                    contentDescription = stringResource(R.string.provider_completion_photo_preview, index + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp),
                )
                EvidenceSelectionStatus.Preparing -> CircularProgressIndicator(Modifier.size(32.dp))
                is EvidenceSelectionStatus.Invalid -> Text(stringResource(R.string.provider_completion_photo_invalid),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        when (val status = selection.status) {
            EvidenceSelectionStatus.Preparing -> Text(stringResource(R.string.provider_completion_photo_preparing),
                style = MaterialTheme.typography.bodySmall)
            is EvidenceSelectionStatus.Invalid -> Text(stringResource(status.reason.messageResource()),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            is EvidenceSelectionStatus.Ready -> Text(status.image.originalName,
                style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (selection.status is EvidenceSelectionStatus.Ready) {
            when (val upload = selection.uploadStatus) {
                EvidenceUploadStatus.NotStarted -> Text(stringResource(R.string.provider_completion_photo_ready),
                    style = MaterialTheme.typography.bodySmall)
                EvidenceUploadStatus.Uploading -> Text(stringResource(R.string.provider_completion_photo_uploading),
                    style = MaterialTheme.typography.bodySmall)
                is EvidenceUploadStatus.Confirmed -> Text(stringResource(R.string.provider_completion_photo_confirmed),
                    style = MaterialTheme.typography.bodySmall)
                is EvidenceUploadStatus.Failed -> {
                    Text(stringResource(upload.failure.stage.messageResource()),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    val retryDescription = stringResource(R.string.provider_completion_photo_retry_description, index + 1)
                    TextButton(onClick = { onRetry(selection.id) }, enabled = editable,
                        modifier = Modifier.testTag("completion_retry_${selection.id}")
                            .semantics { contentDescription = retryDescription }) {
                        Text(stringResource(R.string.provider_completion_photo_retry))
                    }
                }
            }
        }
        val removeDescription = stringResource(R.string.provider_completion_photo_remove_description, index + 1)
        TextButton(onClick = { onRemove(selection.id) }, enabled = editable,
            modifier = Modifier.semantics { contentDescription = removeDescription }) {
            Text(stringResource(R.string.provider_completion_photo_remove))
        }
    }
}

private fun CompletionUploadStage.messageResource(): Int = when (this) {
    CompletionUploadStage.PRESIGN -> R.string.provider_completion_photo_presign_failed
    CompletionUploadStage.LOCAL_FILE -> R.string.provider_completion_photo_read_failed
    CompletionUploadStage.TRANSFER -> R.string.provider_completion_photo_transfer_failed
    CompletionUploadStage.CONFIRM -> R.string.provider_completion_photo_confirm_failed
}

private fun EvidenceImagePreparation.Invalid.messageResource(): Int = when (this) {
    EvidenceImagePreparation.Invalid.UnsupportedFormat -> R.string.provider_completion_photo_unsupported
    EvidenceImagePreparation.Invalid.ExceedsMaxSize -> R.string.provider_completion_photo_oversize
    EvidenceImagePreparation.Invalid.EmptyFile,
    EvidenceImagePreparation.Invalid.CorruptContent -> R.string.provider_completion_photo_bad_file
    EvidenceImagePreparation.Invalid.Unreadable -> R.string.provider_completion_photo_unreadable
}
