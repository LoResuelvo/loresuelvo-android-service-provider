package com.loresuelvo.serviceprovider.ui.components.bottomnav

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun LoresuelvoBottomBar(
    currentRoute: String?,
    onNavigate: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!BottomDestination.shouldShow(currentRoute)) return

    Surface(
        shape = androidx.compose.ui.graphics.RectangleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag(PROVIDER_BOTTOM_BAR_TAG),
    ) {
        Row(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomDestination.all.forEach { destination ->
                val isSelected = currentRoute == destination.route
                val label = stringResource(destination.labelRes)

                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp))
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onNavigate(destination) })
                        .semantics(mergeDescendants = true) { selected = isSelected; contentDescription = label }
                        .testTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + destination.route)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(width = 56.dp, height = 32.dp).background(
                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Icon(destination.icon, contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                    androidx.compose.material3.Text(label,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Compose testTags for the provider navigation bar.
 *
 * The bar exposes two tag surfaces:
 *  - [PROVIDER_BOTTOM_BAR_TAG] — the outer Surface so callers can
 *    assert "the bar is rendered / not rendered".
 *  - [PROVIDER_BOTTOM_BAR_ITEM_PREFIX]` + route — one per tab; lets
 *    a test target the click target without depending on the
 *    localised label copy (the bar includes visible labels).
 */
const val PROVIDER_BOTTOM_BAR_TAG: String = "provider-bottom-bar"
const val PROVIDER_BOTTOM_BAR_ITEM_PREFIX: String = "bottom-bar-item-"
