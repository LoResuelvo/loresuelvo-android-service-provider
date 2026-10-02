package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CalendarConnectionStatus
import com.loresuelvo.serviceprovider.ui.profile.CalendarFeedback
import com.loresuelvo.serviceprovider.ui.profile.ProfileCalendarUiState

const val PROFILE_CALENDAR_STATUS_TAG = "profile-calendar-status"
const val PROFILE_CALENDAR_ACTION_TAG = "profile-calendar-action"
const val PROFILE_CALENDAR_LOADING_TAG = "profile-calendar-loading"

@Composable
internal fun ProfileCalendarCard(
    status: CalendarConnectionStatus,
    state: ProfileCalendarUiState,
    onAuthorize: () -> Unit,
    onRetryConfirmation: () -> Unit,
    onReloadProfile: () -> Unit,
    actionEnabled: Boolean = true,
) {
    ProfileSectionCard {
        ProfileSectionHeading(R.string.provider_profile_calendar_label, Icons.Outlined.CalendarMonth)
        ProfileStatus(stringResource(when (status) {
            CalendarConnectionStatus.Unavailable -> R.string.provider_profile_calendar_unavailable
            CalendarConnectionStatus.Disconnected -> R.string.provider_profile_calendar_disconnected
            CalendarConnectionStatus.Connected -> R.string.provider_profile_calendar_connected
            CalendarConnectionStatus.ActionRequired -> R.string.provider_profile_calendar_action_required
        }), confirmed = status == CalendarConnectionStatus.Connected,
            modifier = Modifier.testTag(PROFILE_CALENDAR_STATUS_TAG))
        Text(stringResource(R.string.provider_profile_calendar_description),
            style = MaterialTheme.typography.bodyMedium)
        if (state.loading) {
            Row(Modifier.testTag(PROFILE_CALENDAR_LOADING_TAG).semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text(stringResource(R.string.provider_profile_calendar_loading))
            }
        }
        state.feedback?.let { feedback ->
            Text(stringResource(when (feedback) {
                CalendarFeedback.Cancelled -> R.string.provider_profile_calendar_cancelled
                CalendarFeedback.Denied -> R.string.provider_profile_calendar_denied
                CalendarFeedback.ConsentFailed -> R.string.provider_profile_calendar_consent_failed
                CalendarFeedback.SubmissionFailed -> R.string.provider_profile_calendar_submission_failed
                CalendarFeedback.CodeRejected -> R.string.provider_profile_calendar_code_rejected
                CalendarFeedback.ConfirmationFailed -> R.string.provider_profile_calendar_confirmation_failed
            }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                color = MaterialTheme.colorScheme.error)
        }
        if (state.confirmationRetry || status == CalendarConnectionStatus.Unavailable || status.canAuthorize) {
            Button(onClick = when {
                state.confirmationRetry -> onRetryConfirmation
                status == CalendarConnectionStatus.Unavailable -> onReloadProfile
                else -> onAuthorize
            }, enabled = actionEnabled && !state.loading,
                modifier = Modifier.fillMaxWidth().testTag(PROFILE_CALENDAR_ACTION_TAG),
                shape = MaterialTheme.shapes.medium) {
                Text(stringResource(when {
                    state.confirmationRetry || status == CalendarConnectionStatus.Unavailable -> R.string.provider_profile_calendar_retry_query
                    state.feedback == CalendarFeedback.CodeRejected -> R.string.provider_profile_calendar_restart
                    status == CalendarConnectionStatus.ActionRequired -> R.string.provider_profile_calendar_reauthorize
                    else -> R.string.provider_profile_calendar_connect
                }))
            }
        }
    }
}
