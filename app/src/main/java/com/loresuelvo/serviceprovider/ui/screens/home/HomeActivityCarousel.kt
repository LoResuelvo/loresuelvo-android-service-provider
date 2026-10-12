package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import kotlinx.coroutines.launch

@Composable
internal fun <T> HomeActivityCarousel(
    items: List<T>, key: (T) -> Int, tag: String,
    card: @Composable (T, Modifier) -> Unit,
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val firstVisibleIndex by remember(state) { derivedStateOf { state.firstVisibleItemIndex } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = minOf(440.dp, maxWidth - 24.dp)
            LazyRow(state = state, flingBehavior = rememberSnapFlingBehavior(state, snapPosition = SnapPosition.Start),
                modifier = Modifier.fillMaxWidth().testTag(tag),
                contentPadding = PaddingValues(end = maxWidth - cardWidth),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = key) { card(it, Modifier.width(cardWidth)) }
            }
        }
        if (items.size > 1) {
            val index = firstVisibleIndex.coerceAtMost(items.lastIndex)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.home_carousel_hint), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { scope.launch { state.animateScrollToItem(index - 1) } },
                    enabled = index > 0, modifier = Modifier.testTag("${tag}_previous")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.home_carousel_previous))
                }
                Text(stringResource(R.string.home_carousel_position, index + 1, items.size),
                    style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { scope.launch { state.animateScrollToItem(index + 1) } },
                    enabled = index < items.lastIndex, modifier = Modifier.testTag("${tag}_next")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.home_carousel_next))
                }
            }
        }
    }
}
