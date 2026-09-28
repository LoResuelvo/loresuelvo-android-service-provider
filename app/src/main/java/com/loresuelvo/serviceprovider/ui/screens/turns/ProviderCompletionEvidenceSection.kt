package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderCompletionReport
import java.util.TimeZone

@Composable
internal fun ProviderCompletionEvidenceSection(report: WorkOrderCompletionReport?,
    onPhotoClick: ((String) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.provider_order_evidence_title),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (report == null) {
            Text(stringResource(R.string.provider_order_evidence_missing))
            return@Column
        }
        report.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        report.reportedOn?.let { reportedOn ->
            Text(formatTurnDate(reportedOn, stringResource(R.string.provider_turns_visit_pattern),
                LocalConfiguration.current.locales[0], TimeZone.getDefault()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (report.images.isEmpty()) {
            Text(stringResource(R.string.provider_order_evidence_photos_missing))
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("provider_evidence_photos"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                report.images.forEachIndexed { index, image ->
                    val label = stringResource(R.string.provider_order_evidence_photo,
                        index + 1, image.originalName.ifBlank { (index + 1).toString() })
                    Box(Modifier.size(96.dp).testTag("provider_evidence_photo_${index + 1}")
                        .then(if (onPhotoClick == null) Modifier else Modifier.clickable(onClickLabel = label) {
                            onPhotoClick(image.fileId)
                        }),
                        contentAlignment = Alignment.Center) {
                        SubcomposeAsyncImage(model = image.url, contentDescription = label,
                            contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp),
                            loading = { CircularProgressIndicator(Modifier.size(32.dp)) },
                            error = { Icon(Icons.Filled.BrokenImage,
                                contentDescription = stringResource(R.string.provider_order_evidence_photo_error),
                                tint = MaterialTheme.colorScheme.error) })
                    }
                }
            }
        }
    }
}
