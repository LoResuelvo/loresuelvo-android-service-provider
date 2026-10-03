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
import androidx.compose.foundation.layout.widthIn
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

    // "Floating dock" / capsule. A single rounded [Surface] hosts
    // the icons with `SpaceEvenly` so the capsule hugs its content
    // (capped at `widthIn(max = …)` so it never bleeds to the screen
    // edges). `RoundedCornerShape(50)` gives the pill geometry on
    // any aspect ratio without ever spilling past the row's height
    // / 2. The bar's tonalElevation stays at zero to keep the M3
    // surface-tint overlay off the background — only a soft
    // `shadowElevation = 3.dp` projects the floating drop.
    // `navigationBarsPadding()` keeps the dock above the Android
    // gesture bar without painting a backdrop.
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 3.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .padding(horizontal = 24.dp)
            .widthIn(min = 240.dp, max = 400.dp)
            .testTag(PROVIDER_BOTTOM_BAR_TAG),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                    Box(Modifier.size(32.dp).background(
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
 * Compose testTags for the provider floating capsule bar.
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
