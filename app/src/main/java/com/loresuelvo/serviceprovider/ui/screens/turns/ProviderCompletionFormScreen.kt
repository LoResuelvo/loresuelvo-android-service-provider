package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder

enum class CompletionFormAvailability {
    Checking, Eligible, TooEarly, AlreadyReported, Forbidden, ChangedOrder, Unavailable, NotFound, Error,
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ProviderCompletionFormScreen(
    order: WorkOrder,
    availability: CompletionFormAvailability,
    description: String,
    onDescriptionChange: (String) -> Unit,
    onAddPhotos: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    canAddPhotos: Boolean = false,
) {
    Scaffold(topBar = { TopAppBar(
        title = { Text(stringResource(R.string.provider_completion_title)) },
        navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.provider_turns_back)) } },
    ) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(order.consumerName, style = MaterialTheme.typography.titleLarge)
            Text(order.description, style = MaterialTheme.typography.bodyLarge)
            when (availability) {
                CompletionFormAvailability.Checking -> {
                    CircularProgressIndicator(Modifier.testTag("completion_checking"))
                    Text(stringResource(R.string.provider_completion_checking))
                }
                CompletionFormAvailability.TooEarly -> Text(stringResource(R.string.provider_completion_too_early))
                CompletionFormAvailability.AlreadyReported -> Text(stringResource(R.string.provider_completion_already_reported))
                CompletionFormAvailability.Forbidden -> Text(stringResource(R.string.provider_completion_forbidden))
                CompletionFormAvailability.ChangedOrder,
                CompletionFormAvailability.Unavailable,
                CompletionFormAvailability.NotFound,
                CompletionFormAvailability.Error -> {
                    Text(stringResource(when (availability) {
                        CompletionFormAvailability.ChangedOrder -> R.string.provider_completion_changed
                        CompletionFormAvailability.Unavailable -> R.string.provider_completion_unavailable
                        CompletionFormAvailability.NotFound -> R.string.provider_completion_missing
                        else -> R.string.provider_completion_error
                    }))
                    Button(onClick = onRetry) { Text(stringResource(R.string.provider_home_retry)) }
                }
                CompletionFormAvailability.Eligible -> {
                    OutlinedTextField(value = description, onValueChange = onDescriptionChange,
                        label = { Text(stringResource(R.string.provider_completion_description)) },
                        modifier = Modifier.fillMaxWidth().testTag("completion_description"), minLines = 3)
                    OutlinedButton(onClick = onAddPhotos, enabled = canAddPhotos) {
                        Text(stringResource(R.string.provider_completion_add_photos))
                    }
                    Button(onClick = {}, enabled = false, modifier = Modifier.testTag("completion_submit")) {
                        Text(stringResource(R.string.provider_completion_submit))
                    }
                }
            }
        }
    }
}
