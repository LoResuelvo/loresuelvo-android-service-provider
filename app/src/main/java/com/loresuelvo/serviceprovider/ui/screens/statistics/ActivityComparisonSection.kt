package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.*
import java.text.NumberFormat

@Composable
internal fun ActivityComparisonSection(current: ActivityResults, previous: ActivityComparison) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.activity_comparison), style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.activity_period, formatActivityInstant(previous.period.from),
            formatActivityInstant(previous.period.to)), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.activity_comparison_note), style = MaterialTheme.typography.bodySmall)
        val results = previous.results
        val changes = previous.changes
        ComparisonValue(R.string.activity_bookings, current.confirmedBookings, results.confirmedBookings, changes.confirmedBookings)
        ComparisonValue(R.string.activity_completions, current.reportedCompletions, results.reportedCompletions, changes.reportedCompletions)
        ComparisonValue(R.string.activity_paid, current.fullyPaidWorkOrders, results.fullyPaidWorkOrders, changes.fullyPaidWorkOrders)
        ComparisonValue(R.string.activity_clients, current.clientsServed, results.clientsServed, changes.clientsServed)
        ComparisonValue(R.string.activity_new_clients, current.newClients, results.newClients, changes.newClients)
        ComparisonValue(R.string.activity_returning_clients, current.returningClients, results.returningClients, changes.returningClients)
        ComparisonValue(R.string.activity_agreed_value, current.agreedValueCents, results.agreedValueCents, changes.agreedValueCents, true)
        ComparisonValue(R.string.activity_average, current.averageValueCents, results.averageValueCents, changes.averageValueCents, true)
    }
}

@Composable
internal fun ComparisonValue(label: Int, current: Long?, previous: Long?, change: ActivityChange, money: Boolean = false) {
    val unavailable = stringResource(R.string.activity_unavailable)
    fun value(number: Long?) = number?.let { if (money) formatActivityMoney(it) else it.toString() } ?: unavailable
    val locale = LocalConfiguration.current.locales[0]
    val percentage = change.percentage?.let {
        NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(it) + "%"
    } ?: unavailable
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.activity_comparison_values, value(current), value(previous)))
        Text(stringResource(R.string.activity_comparison_change, value(change.absolute), percentage))
    }
}
