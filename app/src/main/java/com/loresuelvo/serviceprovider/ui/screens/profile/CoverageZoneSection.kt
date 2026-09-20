package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.coverage.CoverageZone
import com.loresuelvo.serviceprovider.ui.profile.CoverageZonesLoadState

@Composable
internal fun CoverageZoneSection(
    state: CoverageZonesLoadState,
    selectedZoneIds: List<Int>,
    selectionAdjusted: Boolean,
    mapId: String,
    enabled: Boolean,
    onCheckedChange: (Int, Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mapUnavailable by remember(mapId) { mutableStateOf(!isCoverageMapConfigured(mapId)) }
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
            is CoverageZonesLoadState.Ready -> {
                if (mapUnavailable) {
                    Text(
                        text = stringResource(R.string.provider_profile_coverage_map_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    CoverageZoneMap(
                        mapId = mapId,
                        zones = state.zones,
                        selectedZoneIds = selectedZoneIds,
                        enabled = enabled,
                        onCheckedChange = onCheckedChange,
                        onUnavailable = { mapUnavailable = true },
                    )
                }
                val selectedNames = state.zones.filter { it.id in selectedZoneIds }.map { it.name }
                CoverageZoneDropdown(
                    zones = state.zones,
                    selectedZoneIds = selectedZoneIds,
                    selectedNames = selectedNames,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                )
            }
            is CoverageZonesLoadState.Error -> CoverageZonesError(onRetry)
            is CoverageZonesLoadState.Empty -> CoverageZonesEmpty(onRetry)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverageZoneDropdown(
    zones: List<CoverageZone>,
    selectedZoneIds: List<Int>,
    selectedNames: List<String>,
    enabled: Boolean,
    onCheckedChange: (Int, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = if (selectedNames.isEmpty()) "" else stringResource(
        R.string.provider_profile_coverage_selection_summary,
        selectedZoneIds.size,
        selectedNames.joinToString(),
    )
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = summary,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.provider_profile_coverage_dropdown_label)) },
            placeholder = { Text(stringResource(R.string.provider_profile_coverage_placeholder)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            zones.forEach { zone ->
                val checked = zone.id in selectedZoneIds
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(zone.name)
                        }
                    },
                    onClick = { onCheckedChange(zone.id, !checked) },
                )
            }
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
