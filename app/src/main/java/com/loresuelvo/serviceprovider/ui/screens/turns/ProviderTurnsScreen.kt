package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsUiState
import com.loresuelvo.serviceprovider.ui.turns.ProviderTurnsViewModel

@Composable
fun ProviderTurnsRoute(onBack: () -> Unit, viewModel: ProviderTurnsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProviderTurnsScreen(state, onBack, viewModel::load)
}

@Composable
fun ProviderTurnsScreen(state: ProviderTurnsUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.provider_turns_back)) }
            Text(stringResource(R.string.provider_turns_title), style = MaterialTheme.typography.headlineSmall)
            when (state) {
                ProviderTurnsUiState.Loading -> CircularProgressIndicator()
                ProviderTurnsUiState.Error -> Button(onClick = onRetry) {
                    Text(stringResource(R.string.provider_home_retry))
                }
                is ProviderTurnsUiState.Ready -> if (state.orders.isEmpty()) {
                    Text(stringResource(R.string.provider_turns_empty))
                } else LazyColumn(Modifier.fillMaxWidth().testTag("provider_turns_list")) {
                    items(state.orders, key = { it.id }) { order ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("provider_turn_${order.id}")) {
                            Text(order.consumerName, style = MaterialTheme.typography.titleMedium)
                            Text(order.description)
                        }
                    }
                }
            }
        }
    }
}
