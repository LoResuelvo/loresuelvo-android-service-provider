package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R

enum class PerformanceSection { ACTIVITY, COLLECTIONS, REPUTATION }

@Composable
internal fun PerformanceTabs(selected: PerformanceSection, onSelect: (PerformanceSection) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val tabWidth = maxWidth / PerformanceSection.entries.size
    ScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 0.dp, containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.secondary,
        indicator = { positions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[selected.ordinal]),
            color = MaterialTheme.colorScheme.secondary) }) {
        PerformanceSection.entries.forEach { section ->
            Tab(selected = selected == section, onClick = { onSelect(section) },
                modifier = Modifier.widthIn(min = tabWidth).heightIn(min = 48.dp), selectedContentColor = MaterialTheme.colorScheme.secondary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = { Icon(when (section) {
                    PerformanceSection.ACTIVITY -> Icons.Outlined.BarChart
                    PerformanceSection.COLLECTIONS -> Icons.Outlined.AccountBalanceWallet
                    PerformanceSection.REPUTATION -> Icons.Outlined.StarOutline
                }, null) }, text = {
                    Text(stringResource(when (section) {
                        PerformanceSection.ACTIVITY -> R.string.activity_section
                        PerformanceSection.COLLECTIONS -> R.string.collections_section
                        PerformanceSection.REPUTATION -> R.string.reputation_section
                    }))
                })
        }
    }
}
}
