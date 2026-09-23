package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.ui.components.ProviderAvatar

@Composable
internal fun ProfileSummaryCard(provider: CurrentAccount.Provider) {
    ProfileSectionCard {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileAvatar(provider)
                    ProfileNameAndCategory(provider)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileAvatar(provider)
                    ProfileNameAndCategory(provider, Modifier.weight(1f))
                }
            }
        }
        ProfileSampleRating()
    }
}

@Composable
private fun ProfileAvatar(provider: CurrentAccount.Provider) {
    val fullName = "${provider.name} ${provider.surname}".trim()
    ProviderAvatar(
        name = provider.name,
        surname = provider.surname,
        profilePhotoUrl = provider.profilePhotoUrl,
        contentDescription = stringResource(
            if (provider.profilePhotoUrl.isNullOrBlank()) R.string.provider_profile_avatar_description
            else R.string.provider_profile_photo_description,
            fullName,
        ),
        size = 80.dp,
    )
}

@Composable
private fun ProfileNameAndCategory(provider: CurrentAccount.Provider, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${provider.name} ${provider.surname}".trim(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }.testTag(PROVIDER_PROFILE_NAME_TAG),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
        ) {
            Text(
                text = provider.category.name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ProfileSampleRating() {
    // ponytail: display-only Figma sample; replace with provider ratings when that read flow is added.
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("provider-profile-rating-demo"),
        color = MaterialTheme.colorScheme.background,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Star, contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.provider_profile_rating, 4.9),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.provider_profile_reviews, 48, 48),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(R.string.provider_profile_sample_rating),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("provider-profile-rating-demo-label"),
            )
        }
    }
}
