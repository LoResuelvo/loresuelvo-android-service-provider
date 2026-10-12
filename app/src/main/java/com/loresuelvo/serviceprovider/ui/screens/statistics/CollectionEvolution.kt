package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.*

@Composable
internal fun CollectionEvolution(buckets: List<CollectionBucket>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    EvolutionChart(R.string.collections_evolution,
        buckets.map { EvolutionPoint(it.from, it.to, listOf(it.amounts.bookingDepositCents, it.amounts.serviceBalanceCents, it.amounts.totalCents)) },
        listOf(R.string.collections_deposits, R.string.collections_balances, R.string.collections_verified_total),
        money = true, selectedIndex = selectedIndex, onSelect = onSelect)
}

@Composable
internal fun CollectionComparisonSection(current: CollectionAmounts, previous: CollectionComparison) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.activity_comparison), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.activity_period, formatActivityInstant(previous.period.from),
            formatActivityInstant(previous.period.to)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.activity_comparison_note), style = MaterialTheme.typography.bodySmall)
        ComparisonValue(R.string.collections_deposits, current.bookingDepositCents,
            previous.results.bookingDepositCents, previous.changes.bookingDepositCents, true)
        ComparisonValue(R.string.collections_balances, current.serviceBalanceCents,
            previous.results.serviceBalanceCents, previous.changes.serviceBalanceCents, true)
        ComparisonValue(R.string.collections_verified_total, current.totalCents,
            previous.results.totalCents, previous.changes.totalCents, true)
    }
}
