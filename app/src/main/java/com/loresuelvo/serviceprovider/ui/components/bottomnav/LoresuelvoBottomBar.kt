package com.loresuelvo.serviceprovider.ui.components.bottomnav

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomDestination.all.forEach { destination ->
                val isSelected = currentRoute == destination.route

                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(48.dp)
                        // `clip(CircleShape)` MUST sit **before**
                        // `clickable` so the [Box]'s rectangular
                        // bounds stop clipping the ripple —
                        // `clickable`'s default indication is rendered
                        // at the end of the modifier chain and is
                        // clipped by every `clip` that came before
                        // it. Without this clip the ripple painted a
                        // visible square around the icon.
                        .clip(CircleShape)
                        .clickable(onClick = { onNavigate(destination) })
                        .semantics { selected = isSelected }
                        .testTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + destination.route),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        // Selected bubble: a circular
                        // `secondaryContainer` painted **before** the
                        // [Icon] so the icon rests on top of the
                        // bubble. The 48.dp circle reads as a subtle
                        // "medallion" inside the capsule rather than
                        // a card behind the dock.
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = CircleShape,
                                ),
                        )
                    }
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.labelRes),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(if (isSelected) 30.dp else 27.dp),
                    )
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
 *    localised label copy (the bar is icon-only).
 */
const val PROVIDER_BOTTOM_BAR_TAG: String = "provider-bottom-bar"
const val PROVIDER_BOTTOM_BAR_ITEM_PREFIX: String = "bottom-bar-item-"
