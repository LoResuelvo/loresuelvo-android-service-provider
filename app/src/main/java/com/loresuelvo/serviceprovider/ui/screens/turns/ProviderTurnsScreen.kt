package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsViewModel
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar
import com.loresuelvo.serviceprovider.ui.screens.proposals.proposalAmount
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import androidx.compose.foundation.shape.RoundedCornerShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ProviderTurnsRoute(onBack: () -> Unit, onConversation: (Int) -> Unit = {},
    viewModel: ProviderTurnsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val conversationToOpen = (state as? ProviderTurnsUiState.Ready)?.conversationToOpen
    LaunchedEffect(conversationToOpen) {
        if (conversationToOpen != null) {
            viewModel.conversationOpened()
            onConversation(conversationToOpen)
        }
    }
    ProviderTurnsScreen(state, onBack, viewModel::load, onConversation = onConversation,
        onRetryConversation = viewModel::retryConversation)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProviderTurnsScreen(state: ProviderTurnsUiState, onBack: () -> Unit, onRetry: () -> Unit,
    onDetails: (WorkOrder) -> Unit = {}, onConversation: (Int) -> Unit = {},
    onRetryConversation: (Int) -> Unit = { onRetry() }) {
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var missingConversation by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val selectedOrder = (state as? ProviderTurnsUiState.Ready)?.orders?.firstOrNull { it.id == selectedId }
    BackHandler(selectedOrder != null) { selectedId = null }
    if (selectedOrder != null) {
        ProviderTurnDetailScreen(selectedOrder, onBack = { selectedId = null; missingConversation = false },
            onConversation = {
                val conversationId = (state as? ProviderTurnsUiState.Ready)?.conversationIds?.get(selectedOrder.id)
                if (conversationId == null) missingConversation = true else onConversation(conversationId)
            }, missingConversation = missingConversation &&
                (state as ProviderTurnsUiState.Ready).conversationIds[selectedOrder.id] == null,
            proposalFailure = (state as ProviderTurnsUiState.Ready).proposalFailure,
            resolvingConversation = state.resolvingConversation,
            onRetryConversation = { onRetryConversation(selectedOrder.id) })
        return
    }
    Surface(Modifier.fillMaxSize()) {
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_turns_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.provider_turns_back)) }
                },
            )
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    ProviderTurnsUiState.Loading -> Column(
                        Modifier.fillMaxWidth().fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val loading = stringResource(R.string.provider_turns_loading)
                        CircularProgressIndicator(Modifier.testTag("provider_turns_loading")
                            .semantics { contentDescription = loading })
                        Text(loading, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    ProviderTurnsUiState.Error -> Column(
                        Modifier.fillMaxSize().testTag("provider_turns_error"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(stringResource(R.string.provider_turns_error),
                            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.provider_home_retry))
                        }
                    }
                    is ProviderTurnsUiState.Ready -> if (state.orders.isEmpty()) {
                        Column(
                            Modifier.fillMaxSize().testTag("provider_turns_empty"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(stringResource(R.string.provider_turns_empty),
                                style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                            Text(stringResource(R.string.provider_turns_empty_body),
                                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center)
                        }
                    } else LazyColumn(Modifier.fillMaxWidth().testTag("provider_turns_list"), state = listState) {
                        items(state.orders, key = { it.id }) { order ->
                            ProviderTurnCard(order) { selectedId = order.id; missingConversation = false; onDetails(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderTurnCard(order: WorkOrder, onDetails: (WorkOrder) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val datePattern = stringResource(R.string.provider_turns_visit_pattern)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("provider_turn_${order.id}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ProviderAvatar(
                    name = order.consumerGivenName,
                    surname = order.consumerSurname,
                    profilePhotoUrl = order.consumerPhotoUrl,
                    contentDescription = stringResource(R.string.proposal_list_avatar, order.consumerGivenName),
                    size = 56.dp,
                )
                Text(order.consumerName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                ProviderTurnStatusBadge(order.id, order.status)
            }
            Text(order.description)
            Text(proposalAmount(order.amountCents))
            Text(formatTurnDate(order.scheduledOn, datePattern, locale, TimeZone.getDefault()))
            OutlinedButton(onClick = { onDetails(order) }, modifier = Modifier.testTag("provider_turn_details_${order.id}")) {
                Text(stringResource(R.string.provider_turns_view_details))
            }
        }
    }
}

@Composable
private fun ProviderTurnStatusBadge(orderId: Int, status: WorkOrderStatus) {
    val badge = providerTurnStatusBadge(status) ?: return
    val (background, foreground) = when (badge.treatment) {
        ProviderTurnBadgeTreatment.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        ProviderTurnBadgeTreatment.Error -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        ProviderTurnBadgeTreatment.Neutral -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier = Modifier.testTag("provider_turn_status_$orderId"), shape = RoundedCornerShape(50), color = background) {
        Text(stringResource(badge.label), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = foreground,
            style = MaterialTheme.typography.labelSmall)
    }
}

internal enum class ProviderTurnBadgeTreatment { Primary, Error, Neutral }
internal data class ProviderTurnBadge(val label: Int, val treatment: ProviderTurnBadgeTreatment)

internal fun providerTurnStatusBadge(status: WorkOrderStatus): ProviderTurnBadge? = when (status) {
    WorkOrderStatus.Scheduled -> ProviderTurnBadge(R.string.provider_turns_status_scheduled, ProviderTurnBadgeTreatment.Primary)
    WorkOrderStatus.AwaitingPayment -> ProviderTurnBadge(R.string.provider_turns_status_awaiting_payment, ProviderTurnBadgeTreatment.Error)
    WorkOrderStatus.Paid -> ProviderTurnBadge(R.string.provider_turns_status_paid, ProviderTurnBadgeTreatment.Neutral)
    is WorkOrderStatus.Unsupported -> null
}

internal fun formatTurnDate(epochMillis: Long, pattern: String, locale: Locale, timeZone: TimeZone): String =
    SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }.format(Date(epochMillis))
