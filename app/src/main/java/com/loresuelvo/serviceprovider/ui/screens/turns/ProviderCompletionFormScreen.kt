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
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.ui.turns.CompletionEvidenceSelection
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionIssue
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionStatus
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
) {
    Scaffold(topBar = { TopAppBar(
        title = { Text(stringResource(R.string.provider_completion_title)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.provider_turns_back)) } },
    ) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(order.consumerName, style = MaterialTheme.typography.titleLarge)
            Text(order.description, style = MaterialTheme.typography.bodyLarge)
            when (availability) {
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
                    OutlinedTextField(value = description, onValueChange = onDescriptionChange,
                        label = { Text(stringResource(R.string.provider_completion_description)) },
                        modifier = Modifier.fillMaxWidth().testTag("completion_description"), minLines = 3)
                    Text(stringResource(R.string.provider_completion_photos), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.provider_completion_photo_limit),
                        style = MaterialTheme.typography.bodySmall)
                    if (evidence.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            evidence.forEachIndexed { index, selection ->
                                CompletionEvidenceItem(index, selection, onRemoveEvidence)
                            }
                        }
                    }
                    if (evidenceIssue != null) {
                        Text(stringResource(when (evidenceIssue) {
                            EvidenceSelectionIssue.MaximumReached -> R.string.provider_completion_photo_maximum
                            EvidenceSelectionIssue.AlreadySelected -> R.string.provider_completion_photo_duplicate
                        }), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = onAddPhotos, enabled = canAddPhotos) {
                        Text(stringResource(R.string.provider_completion_add_photos))
                    }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.provider_completion_cancel)) }
                    Button(onClick = {}, enabled = false, modifier = Modifier.testTag("completion_submit")) {
                        Text(stringResource(R.string.provider_completion_submit))
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionEvidenceItem(
    index: Int,
    selection: CompletionEvidenceSelection,
    onRemove: (Long) -> Unit,
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
        val removeDescription = stringResource(R.string.provider_completion_photo_remove_description, index + 1)
        TextButton(onClick = { onRemove(selection.id) },
            modifier = Modifier.semantics { contentDescription = removeDescription }) {
            Text(stringResource(R.string.provider_completion_photo_remove))
        }
    }
}

private fun EvidenceImagePreparation.Invalid.messageResource(): Int = when (this) {
    EvidenceImagePreparation.Invalid.UnsupportedFormat -> R.string.provider_completion_photo_unsupported
    EvidenceImagePreparation.Invalid.ExceedsMaxSize -> R.string.provider_completion_photo_oversize
    EvidenceImagePreparation.Invalid.EmptyFile,
    EvidenceImagePreparation.Invalid.CorruptContent -> R.string.provider_completion_photo_bad_file
    EvidenceImagePreparation.Invalid.Unreadable -> R.string.provider_completion_photo_unreadable
}
