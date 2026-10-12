package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R

@Composable
internal fun PerformanceHeader(subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.activity_title), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ActivityViewTabs(conversion: Boolean, onSummary: () -> Unit, onConversion: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !conversion, onClick = onSummary,
            label = { Text(stringResource(R.string.performance_summary)) }, modifier = Modifier.heightIn(min = 48.dp))
        FilterChip(selected = conversion, onClick = onConversion,
            label = { Text(stringResource(R.string.performance_conversion)) }, modifier = Modifier.heightIn(min = 48.dp))
    }
}

@Composable
internal fun PerformanceCard(attention: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = if (attention) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (attention) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
