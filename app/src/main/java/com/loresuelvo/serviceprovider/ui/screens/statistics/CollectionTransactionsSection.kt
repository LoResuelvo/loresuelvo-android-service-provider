package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.CollectionPurpose
import com.loresuelvo.serviceprovider.domain.statistics.CollectionTransaction
import com.loresuelvo.serviceprovider.ui.statistics.CollectionTransactionsUiState

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.collectionTransactionsItems(state: CollectionTransactionsUiState,
    onPurpose: (CollectionPurpose?) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit,
    onWorkOrder: (Int) -> Unit = {}, onProposal: (Int) -> Unit = {}) {
    item(key = "collection_transaction_filters") {
        HorizontalDivider()
        Text(stringResource(R.string.collections_transactions), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, CollectionPurpose.BOOKING_DEPOSIT, CollectionPurpose.SERVICE_BALANCE).forEach { purpose ->
                FilterChip(selected = state.purpose == purpose, onClick = { onPurpose(purpose) },
                    enabled = !state.sessionExpired,
                    label = { Text(stringResource(purposeLabel(purpose))) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    if (state.sessionExpired) {
        item { Text(stringResource(R.string.collections_session_expired)) }
        return
    }
    state.page?.let { page ->
        item {
            Text(stringResource(R.string.collections_transactions_totals, page.totalCount,
                formatActivityMoney(page.totalAmountCents)), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.activity_calculated, formatActivityInstant(page.calculatedAt)),
                style = MaterialTheme.typography.bodySmall)
        }
        if (page.transactions.isEmpty()) item { Text(stringResource(R.string.collections_transactions_empty)) }
        items(page.transactions, key = { "collection_transaction_${it.id}" }) { row -> TransactionRow(row, onWorkOrder, onProposal) }
    }
    if (state.loading) item {
        CircularProgressIndicator()
        Text(stringResource(R.string.collections_transactions_loading))
    }
    if (state.failure != null) item {
        Text(stringResource(if (state.restartRequired) R.string.collections_transactions_restart else R.string.collections_transactions_error))
        Button(onClick = onRetry, enabled = !state.loading) { Text(stringResource(R.string.activity_retry)) }
    } else if (state.page?.nextCursor != null) item {
        Button(onClick = onLoadMore, enabled = !state.loading) { Text(stringResource(R.string.collections_load_more)) }
    }
}

@Composable
private fun TransactionRow(row: CollectionTransaction, onWorkOrder: (Int) -> Unit, onProposal: (Int) -> Unit) {
    Surface(modifier = Modifier.testTag("collection_transaction_${row.id}"),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(16.dp)) {
            Text(stringResource(purposeLabel(row.purpose)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.collections_verified_on, formatActivityInstant(row.verifiedOn)))
            Text(formatActivityMoney(row.sellerAmountCents), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { row.workOrderId?.let(onWorkOrder) ?: onProposal(row.serviceProposalId) },
                modifier = Modifier.testTag("collection_detail_${row.id}").heightIn(min = 48.dp)) {
                Text(stringResource(if (row.workOrderId != null) R.string.performance_view_work else R.string.performance_view_proposal))
            }
        }
    }
}

private fun purposeLabel(purpose: CollectionPurpose?) = when (purpose) {
    null -> R.string.collections_filter_all
    CollectionPurpose.BOOKING_DEPOSIT -> R.string.collections_filter_deposits
    CollectionPurpose.SERVICE_BALANCE -> R.string.collections_filter_balances
}
