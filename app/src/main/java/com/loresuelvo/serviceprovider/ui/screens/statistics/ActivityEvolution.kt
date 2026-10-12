package com.loresuelvo.serviceprovider.ui.screens.statistics

import androidx.compose.runtime.Composable
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.ActivityBucket

@Composable
internal fun ActivityEvolution(buckets: List<ActivityBucket>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    EvolutionChart(R.string.activity_evolution,
        buckets.map { EvolutionPoint(it.from, it.to, listOf(it.confirmedBookings, it.reportedCompletions, it.fullyPaidWorkOrders)) },
        listOf(R.string.activity_bookings, R.string.activity_completions, R.string.activity_paid),
        money = false, selectedIndex = selectedIndex, onSelect = onSelect)
}
