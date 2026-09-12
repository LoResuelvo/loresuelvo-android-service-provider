package com.loresuelvo.serviceprovider.ui.screens.profile

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.profile.SelectedProfilePhoto
import com.loresuelvo.serviceprovider.ui.components.buttons.PrimaryButton
import com.loresuelvo.serviceprovider.ui.profile.PhotoFormError

/**
 * Section for selecting, previewing, and initiating upload of the provider profile photo.
 */
@Composable
fun ProfilePhotoSection(
    selectedPhoto: SelectedProfilePhoto?,
    isPhotoConfirmed: Boolean,
    photoLoading: Boolean,
    photoError: PhotoFormError?,
    onSelectPhoto: () -> Unit,
    onUploadPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    formLoading: Boolean = false,
) {
    val controlsEnabled = !photoLoading && !formLoading

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.provider_profile_photo_section_title),
            style = MaterialTheme.typography.titleMedium,
        )

        if (selectedPhoto != null) {
            PhotoPreviewCard(
                photo = selectedPhoto,
                isConfirmed = isPhotoConfirmed,
                isLoading = photoLoading,
                controlsEnabled = controlsEnabled,
                hasError = photoError != null,
                onChangePhoto = onSelectPhoto,
                onUploadPhoto = onUploadPhoto,
            )
        } else {
            OutlinedButton(
                onClick = onSelectPhoto,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = controlsEnabled,
            ) {
                Text(text = stringResource(R.string.provider_profile_photo_select))
            }
        }

        if (photoError != null) {
            val errorMessage = when (photoError) {
                is PhotoFormError.UnsupportedFormat -> stringResource(R.string.provider_profile_photo_error_format)
                is PhotoFormError.ExceedsMaxSize -> stringResource(R.string.provider_profile_photo_error_size)
                is PhotoFormError.EmptyFile -> stringResource(R.string.provider_profile_photo_error_empty)
                is PhotoFormError.Unreadable -> stringResource(R.string.provider_profile_photo_error_unreadable)
                is PhotoFormError.CorruptContent -> stringResource(R.string.provider_profile_photo_error_corrupt)
                is PhotoFormError.UploadFailed -> stringResource(R.string.provider_profile_photo_error_upload)
            }
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PhotoPreviewCard(
    photo: SelectedProfilePhoto,
    isConfirmed: Boolean,
    isLoading: Boolean,
    controlsEnabled: Boolean,
    hasError: Boolean,
    onChangePhoto: () -> Unit,
    onUploadPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(photo.localPath) {
        try {
            BitmapFactory.decodeFile(photo.localPath)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.provider_profile_photo_preview_description),
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                ) {}
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.provider_profile_photo_uploading),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else if (isConfirmed) {
                    Text(
                        text = stringResource(R.string.provider_profile_photo_ready),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    PrimaryButton(
                        text = if (hasError) {
                            stringResource(R.string.provider_profile_retry)
                        } else {
                            stringResource(R.string.provider_profile_photo_upload)
                        },
                        onClick = onUploadPhoto,
                        enabled = controlsEnabled,
                    )
                }

                OutlinedButton(
                    onClick = onChangePhoto,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = controlsEnabled,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = stringResource(R.string.provider_profile_photo_change))
                }
            }
        }
    }
}
