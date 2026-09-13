package com.loresuelvo.serviceprovider.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeUiState

@Composable
fun ProviderHomeScreen(
    provider: CurrentAccount.Provider,
    uiState: ProviderHomeUiState,
    onRetryJobRequests: () -> Unit,
    onRetryScheduledWork: () -> Unit,
    onMercadoPagoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.provider_home_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            ProviderIdentityHeader(provider)
            ProviderActivitySummary(uiState)
            ProviderHomeActions(
                onMercadoPagoClick = onMercadoPagoClick,
            )
            JobRequestsSection(
                state = uiState.jobRequests,
                onRetry = onRetryJobRequests,
            )
            ScheduledWorkSection(
                state = uiState.scheduledWork,
                onRetry = onRetryScheduledWork,
            )
        }
    }
}

@Composable
private fun ProviderIdentityHeader(provider: CurrentAccount.Provider) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProviderAvatar(provider)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.provider_home_greeting,
                    provider.name,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${provider.name} ${provider.surname}".trim(),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = provider.category.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderActivitySummary(uiState: ProviderHomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryItem(
            label = stringResource(R.string.provider_home_requests_count_label),
            count = uiState.jobRequests.countOrPlaceholder(),
            modifier = Modifier.weight(1f),
        )
        SummaryItem(
            label = stringResource(R.string.provider_home_scheduled_count_label),
            count = uiState.scheduledWork.countOrPlaceholder(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryItem(
    label: String,
    count: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = count, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProviderHomeActions(onMercadoPagoClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.provider_home_actions_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .weight(1f)
                    .testTag("provider-home-requests-action"),
            ) {
                Text(stringResource(R.string.provider_home_requests_action))
            }
            OutlinedButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .weight(1f)
                    .testTag("provider-home-scheduled-action"),
            ) {
                Text(stringResource(R.string.provider_home_scheduled_action))
            }
        }
        Button(onClick = onMercadoPagoClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.provider_home_mercadopago_action))
        }
        Text(
            text = stringResource(R.string.provider_home_detail_unavailable),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun <T> ActivitySectionState<T>.countOrPlaceholder(): String = when (this) {
    is ActivitySectionState.Ready -> items.size.toString()
    ActivitySectionState.Error, ActivitySectionState.Loading -> "—"
}
