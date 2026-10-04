package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.heightIn
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
    ScrollableTabRow(selectedTabIndex = selected.ordinal, edgePadding = 0.dp) {
        PerformanceSection.entries.forEach { section ->
            Tab(selected = selected == section, onClick = { onSelect(section) },
                modifier = Modifier.heightIn(min = 48.dp), text = {
                    Text(stringResource(when (section) {
                        PerformanceSection.ACTIVITY -> R.string.activity_section
                        PerformanceSection.COLLECTIONS -> R.string.collections_section
                        PerformanceSection.REPUTATION -> R.string.reputation_section
                    }))
                })
        }
    }
}
