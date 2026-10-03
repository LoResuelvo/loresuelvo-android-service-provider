package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.CollectionPurpose
import com.loresuelvo.serviceprovider.domain.statistics.CollectionTransaction
import com.loresuelvo.serviceprovider.ui.statistics.CollectionTransactionsUiState

@OptIn(ExperimentalLayoutApi::class)
internal fun LazyListScope.collectionTransactionsItems(state: CollectionTransactionsUiState,
    onPurpose: (CollectionPurpose?) -> Unit, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    item {
        HorizontalDivider()
        Text(stringResource(R.string.collections_transactions), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, CollectionPurpose.BOOKING_DEPOSIT, CollectionPurpose.SERVICE_BALANCE).forEach { purpose ->
                FilterChip(selected = state.purpose == purpose, onClick = { onPurpose(purpose) },
                    enabled = !state.sessionExpired,
                    label = { Text(stringResource(purposeLabel(purpose))) })
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
        items(page.transactions, key = { "collection_transaction_${it.id}" }) { row -> TransactionRow(row) }
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
private fun TransactionRow(row: CollectionTransaction) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(16.dp)) {
            Text(stringResource(purposeLabel(row.purpose)), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.collections_verified_on, formatActivityInstant(row.verifiedOn)))
            Text(formatActivityMoney(row.sellerAmountCents), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.collections_proposal_reference, row.serviceProposalId))
            row.workOrderId?.let { Text(stringResource(R.string.collections_order_reference, it)) }
        }
    }
}

private fun purposeLabel(purpose: CollectionPurpose?) = when (purpose) {
    null -> R.string.collections_filter_all
    CollectionPurpose.BOOKING_DEPOSIT -> R.string.collections_filter_deposits
    CollectionPurpose.SERVICE_BALANCE -> R.string.collections_filter_balances
}
