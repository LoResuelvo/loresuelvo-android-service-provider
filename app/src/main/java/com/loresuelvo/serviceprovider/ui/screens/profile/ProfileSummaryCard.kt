package com.loresuelvo.serviceprovider.ui.screens.profile

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
    Surface(Modifier.fillMaxWidth().testTag("profile_header"),
        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().statusBarsPadding().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.provider_profile_my_profile),
                    style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
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
            }
        }
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
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Text(
                text = provider.category.name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
