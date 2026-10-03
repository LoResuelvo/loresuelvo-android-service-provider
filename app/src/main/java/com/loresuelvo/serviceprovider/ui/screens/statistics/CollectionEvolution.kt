package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.*

@Composable
internal fun CollectionEvolutionHeading() {
    Text(stringResource(R.string.activity_evolution), style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() })
    Text(stringResource(R.string.collections_evolution_note), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun CollectionEvolutionBucket(bucket: CollectionBucket) {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.activity_period, formatActivityInstant(bucket.from), formatActivityInstant(bucket.to)),
            style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.collections_bucket_values,
            formatActivityMoney(bucket.amounts.bookingDepositCents), formatActivityMoney(bucket.amounts.serviceBalanceCents),
            formatActivityMoney(bucket.amounts.totalCents)))
    }
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
