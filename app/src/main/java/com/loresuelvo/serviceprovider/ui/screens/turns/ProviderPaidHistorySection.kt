package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderReview
import java.util.TimeZone

@Composable
internal fun ProviderPaidHistorySection(paidOn: Long?, review: WorkOrderReview?) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.provider_order_paid_title),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        paidOn?.let {
            Text(formatTurnDate(it, stringResource(R.string.provider_turns_visit_pattern),
                LocalConfiguration.current.locales[0], TimeZone.getDefault()))
        }
        Text(stringResource(R.string.provider_order_review_title),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (review == null) {
            Text(stringResource(R.string.provider_order_review_missing))
        } else {
            Text(stringResource(R.string.provider_order_review_rating, review.rating))
            review.description?.let { Text(it) }
        }
    }
}
