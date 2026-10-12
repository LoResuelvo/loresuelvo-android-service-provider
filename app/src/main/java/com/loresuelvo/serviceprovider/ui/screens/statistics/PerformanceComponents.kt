package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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

@Composable
internal fun PerformanceCount(value: Long, label: Int, modifier: Modifier = Modifier) {
    val description = androidx.compose.ui.res.pluralStringResource(label, value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    val number = java.text.NumberFormat.getIntegerInstance(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]).format(value)
    Text(androidx.compose.ui.text.buildAnnotatedString {
        pushStyle(MaterialTheme.typography.headlineSmall.toSpanStyle())
        append(number)
        pop()
        append(" ")
        append(description)
    }, modifier = modifier, style = MaterialTheme.typography.bodyLarge)
}

@Composable
internal fun DetailLink(label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(stringResource(label))
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(18.dp))
    }
}
