package com.loresuelvo.serviceprovider.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.loresuelvo.serviceprovider.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshableContent(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val label = stringResource(R.string.ui_refresh)
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().semantics {
            customActions = listOf(CustomAccessibilityAction(label) { onRefresh(); true })
        }, content = content)
}
