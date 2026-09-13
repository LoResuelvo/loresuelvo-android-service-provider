package com.loresuelvo.serviceprovider.ui.screens.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R

@Composable
fun ProviderEntryLoadingScreen(modifier: Modifier = Modifier) {
    ProviderEntrySurface(modifier) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.provider_entry_loading),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun ProviderEntryErrorScreen(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProviderEntrySurface(modifier) {
        Text(
            text = stringResource(R.string.provider_entry_error_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.provider_entry_error_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.provider_entry_retry))
        }
    }
}

@Composable
fun ProviderAccountMismatchScreen(
    onReturnToWelcome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ProviderEntrySurface(modifier) {
        Text(
            text = stringResource(R.string.provider_entry_account_mismatch_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.provider_entry_account_mismatch_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onReturnToWelcome) {
            Text(text = stringResource(R.string.provider_entry_account_mismatch_action))
        }
    }
}

@Composable
private fun ProviderEntrySurface(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            content()
        }
    }
}
