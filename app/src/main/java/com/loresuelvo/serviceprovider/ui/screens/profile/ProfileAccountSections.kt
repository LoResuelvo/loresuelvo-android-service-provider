package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationAction
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.ui.identity.IdentityVerificationFeedback
import com.loresuelvo.serviceprovider.ui.profile.ProfileIdentityUiState
import com.loresuelvo.serviceprovider.ui.profile.ProfilePaymentState
import java.text.DateFormat
import java.util.Date

@Composable
internal fun ProfileSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
internal fun ProfileAccountCard(provider: CurrentAccount.Provider) {
    ProfileSectionCard {
        ProfileSectionHeading(R.string.provider_profile_account_title, Icons.Outlined.Person)
        ProfileDetail(R.string.provider_profile_name_label, provider.name)
        ProfileDivider()
        ProfileDetail(R.string.provider_profile_surname_label, provider.surname)
        ProfileDivider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Email, contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            ProfileDetail(R.string.provider_profile_email_label, provider.email)
        }
    }
}

@Composable
internal fun ProfileIdentityCard(
    provider: CurrentAccount.Provider,
    identity: ProfileIdentityUiState,
    onVerify: () -> Unit,
    onReload: () -> Unit,
) {
    ProfileSectionCard {
        ProfileSectionHeading(R.string.provider_profile_identity_label, Icons.Outlined.Shield)
        ProfileStatus(
            text = stringResource(provider.identityVerificationStatus.labelResource()),
            confirmed = provider.identityVerificationStatus == IdentityVerificationStatus.Approved,
        )
        Text(
            stringResource(R.string.provider_profile_identity_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (provider.identityVerificationStatus == IdentityVerificationStatus.Approved) {
            provider.identityVerifiedOn?.let { verifiedOn ->
                val locale = LocalConfiguration.current.locales[0]
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(verifiedOn))
                ProfileDivider()
                ProfileDetail(R.string.provider_profile_identity_date_label, date)
            }
        }
        ProfileIdentityActions(provider.identityVerificationStatus, identity, onVerify, onReload)
    }
}

const val PROFILE_IDENTITY_ACTION_TAG = "profile-identity-action"

@Composable
private fun ProfileIdentityActions(
    status: IdentityVerificationStatus,
    identity: ProfileIdentityUiState,
    onVerify: () -> Unit,
    onReload: () -> Unit,
) {
    if (identity.loading) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(stringResource(R.string.provider_profile_identity_starting))
        }
    }
    identity.feedback?.let {
        Text(stringResource(when (it) {
            IdentityVerificationFeedback.Cancelled -> R.string.provider_profile_identity_cancelled
            IdentityVerificationFeedback.PermissionDenied -> R.string.provider_profile_identity_permission_denied
            IdentityVerificationFeedback.Failed -> R.string.provider_profile_identity_failed
            IdentityVerificationFeedback.SessionStartFailed -> R.string.provider_profile_identity_session_failed
        }), color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
    }
    Button(onClick = onVerify, enabled = !identity.loading && status.availableAction != null,
        modifier = Modifier.fillMaxWidth().testTag(PROFILE_IDENTITY_ACTION_TAG),
        shape = MaterialTheme.shapes.medium) {
        Text(stringResource(if (status.availableAction == IdentityVerificationAction.Retry)
            R.string.provider_profile_view_retry else R.string.provider_profile_identity_verify))
    }
    if (status == IdentityVerificationStatus.Unavailable) {
        Button(onClick = onReload, enabled = !identity.loading, modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium) {
            Text(stringResource(R.string.provider_profile_identity_reload))
        }
    }
}

@Composable
internal fun ProfilePaymentCard(
    payment: ProfilePaymentState,
    onConnect: () -> Unit,
    onRetry: () -> Unit,
) {
    ProfileSectionCard {
        ProfileSectionHeading(R.string.provider_profile_mercadopago_label, Icons.Outlined.AccountBalanceWallet)
        ProfileStatus(
            text = stringResource(when (payment) {
                ProfilePaymentState.Loading -> R.string.provider_profile_connection_loading
                ProfilePaymentState.Pending -> R.string.provider_profile_connection_pending
                ProfilePaymentState.Connected -> R.string.provider_profile_connection_connected
                ProfilePaymentState.Unavailable -> R.string.provider_profile_connection_unavailable
            }),
            confirmed = payment == ProfilePaymentState.Connected,
        )
        Text(
            stringResource(R.string.provider_profile_payment_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (payment == ProfilePaymentState.Pending) {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.mercadopago_connect_button))
            }
        } else if (payment == ProfilePaymentState.Unavailable) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.provider_profile_connection_retry))
            }
        }
    }
}

@Composable
internal fun ProfileCalendarCard() {
    ProfileSectionCard {
        ProfileSectionHeading(R.string.provider_profile_calendar_label, Icons.Outlined.CalendarMonth)
        ProfileStatus(stringResource(R.string.provider_profile_calendar_coming_soon))
        Text(
            stringResource(R.string.provider_profile_calendar_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileSectionHeading(@StringRes title: Int, icon: ImageVector) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun ProfileStatus(text: String, confirmed: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (confirmed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.background,
        contentColor = if (confirmed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun ProfileDetail(@StringRes labelRes: Int, value: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
}

private fun IdentityVerificationStatus.labelResource(): Int = when (this) {
    IdentityVerificationStatus.Unavailable -> R.string.provider_profile_identity_unavailable
    IdentityVerificationStatus.Unverified -> R.string.provider_profile_identity_unverified
    IdentityVerificationStatus.NotStarted -> R.string.provider_profile_identity_not_started
    IdentityVerificationStatus.InProgress -> R.string.provider_profile_identity_in_progress
    IdentityVerificationStatus.AwaitingUser -> R.string.provider_profile_identity_awaiting_user
    IdentityVerificationStatus.InReview -> R.string.provider_profile_identity_in_review
    IdentityVerificationStatus.Approved -> R.string.provider_profile_identity_approved
    IdentityVerificationStatus.Declined -> R.string.provider_profile_identity_declined
    IdentityVerificationStatus.Resubmitted -> R.string.provider_profile_identity_resubmitted
    IdentityVerificationStatus.Abandoned -> R.string.provider_profile_identity_abandoned
    IdentityVerificationStatus.Expired -> R.string.provider_profile_identity_expired
    IdentityVerificationStatus.KycExpired -> R.string.provider_profile_identity_kyc_expired
}
