package com.loresuelvo.serviceprovider.ui.screens.jobrequest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.ui.components.NavigationBackButton
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar
import com.loresuelvo.serviceprovider.ui.components.RefreshableContent
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobRequestListScreen(
    state: ActivitySectionState<JobRequest>,
    refreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestClick: (JobRequest) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        topBar = { TopAppBar(
            title = { Text(stringResource(R.string.requests_list_title)) },
            navigationIcon = { NavigationBackButton(onClick = onBack) },
        ) },
    ) { padding ->
        RefreshableContent(refreshing = refreshing, onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize().testTag("requests_refresh")) {
            LazyColumn(Modifier.fillMaxSize().testTag("requests_list"),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state == ActivitySectionState.Loading) item { CircularProgressIndicator() }
                if (state == ActivitySectionState.Error || state is ActivitySectionState.Ready && state.refreshFailed) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.provider_home_section_error))
                            Button(onClick = onRefresh) { Text(stringResource(R.string.provider_home_retry)) }
                        }
                    }
                }
                if (state is ActivitySectionState.Ready) {
                    if (state.items.isEmpty()) item { Text(stringResource(R.string.provider_home_requests_empty)) }
                    items(state.items, key = { it.id }) { request ->
                        Surface(Modifier.fillMaxWidth().testTag("request_${request.id}"),
                            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ProviderAvatar(request.consumerName, "", null,
                                        stringResource(R.string.proposal_list_avatar, request.consumerName), size = 48.dp)
                                    Text(request.consumerName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                }
                                Text(request.title, style = MaterialTheme.typography.titleMedium)
                                Text(request.description, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                OutlinedButton(onClick = { onRequestClick(request) },
                                    modifier = Modifier.fillMaxWidth().testTag("request_details_${request.id}")) {
                                    Text(stringResource(R.string.provider_home_view_request))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
