package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.profile.CoverageZonesLoadState

@Composable
internal fun CoverageZoneSection(
    state: CoverageZonesLoadState,
    selectedZoneIds: List<Int>,
    selectionAdjusted: Boolean,
    enabled: Boolean,
    onCheckedChange: (Int, Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.provider_profile_coverage_title),
            style = MaterialTheme.typography.titleMedium,
        )
        if (selectionAdjusted) {
            Text(
                text = stringResource(R.string.provider_profile_coverage_adjusted_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state) {
            is CoverageZonesLoadState.Loading -> CoverageZonesLoading()
            is CoverageZonesLoadState.Ready -> state.zones.forEach { zone ->
                key(zone.id) {
                    val checked = zone.id in selectedZoneIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = checked,
                                enabled = enabled,
                                role = Role.Checkbox,
                                onValueChange = { onCheckedChange(zone.id, it) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                        Text(text = zone.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            is CoverageZonesLoadState.Error -> CoverageZonesError(onRetry)
            is CoverageZonesLoadState.Empty -> CoverageZonesEmpty(onRetry)
        }
    }
}

@Composable
private fun CoverageZonesEmpty(onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.provider_profile_coverage_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onRetry) {
        Text(text = stringResource(R.string.provider_profile_reload))
    }
}

@Composable
private fun CoverageZonesError(onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.provider_profile_coverage_load_error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    OutlinedButton(onClick = onRetry) {
        Text(text = stringResource(R.string.provider_profile_retry))
    }
}

@Composable
private fun CoverageZonesLoading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.provider_profile_coverage_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
