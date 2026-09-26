package com.loresuelvo.serviceprovider.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
internal fun ProviderAvatar(
    name: String,
    surname: String,
    profilePhotoUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val initials = providerInitials(name, surname)
    var photoLoaded by remember(profilePhotoUrl) { mutableStateOf(false) }
    var photoFailed by remember(profilePhotoUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (!photoLoaded) {
            Text(
                text = initials,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!profilePhotoUrl.isNullOrBlank()) {
            AsyncImage(
                model = profilePhotoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { photoLoaded = true; photoFailed = false },
                onError = { photoLoaded = false; photoFailed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .testTag(if (photoFailed) "provider_avatar_photo_error" else "provider_avatar_photo"),
            )
        }
    }
}

internal fun providerInitials(name: String, surname: String): String =
    listOf(name, surname)
        .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
