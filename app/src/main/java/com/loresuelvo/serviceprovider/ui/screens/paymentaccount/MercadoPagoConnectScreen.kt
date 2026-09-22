package com.loresuelvo.serviceprovider.ui.screens.paymentaccount

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.ui.components.buttons.PrimaryButton
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectError
import com.loresuelvo.serviceprovider.ui.paymentaccount.MercadoPagoConnectUiState

@Composable
fun MercadoPagoConnectScreen(
    uiState: MercadoPagoConnectUiState,
    returnToProfile: Boolean = false,
    onConnectClick: () -> Unit = {},
    onContinueWithoutConnecting: () -> Unit = {},
    onContinueHome: () -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.mercadopago_connect_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.mercadopago_connect_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (uiState.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.mercadopago_checking_status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (uiState.isIneligible) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = uiState.ineligibleOrientation ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            when (val error = uiState.error) {
                is MercadoPagoConnectError.Network -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = error.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(text = stringResource(R.string.mercadopago_retry))
                    }
                }
                is MercadoPagoConnectError.Server -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = error.message ?: stringResource(R.string.provider_profile_generic_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(text = stringResource(R.string.mercadopago_retry))
                    }
                }
                is MercadoPagoConnectError.BrowserLaunchFailed -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.mercadopago_error_browser_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(text = stringResource(R.string.mercadopago_retry))
                    }
                }
                else -> Unit
            }

            when (uiState.accountStatus?.status) {
                ConnectionStatus.CONNECTED -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.mercadopago_connected_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.mercadopago_connected_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f, fill = false))
                    PrimaryButton(
                        text = stringResource(
                            if (returnToProfile) R.string.mercadopago_return_profile
                            else R.string.mercadopago_continue_home,
                        ),
                        onClick = onContinueHome,
                        enabled = !uiState.loading,
                    )
                }
                ConnectionStatus.PENDING -> {
                    if (!uiState.isIneligible) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.connectionIncomplete) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(
                                        if (uiState.connectionIncomplete) {
                                            R.string.mercadopago_connection_incomplete_title
                                        } else {
                                            R.string.mercadopago_pending_title
                                        }
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (uiState.connectionIncomplete) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        if (uiState.connectionIncomplete) {
                                            R.string.mercadopago_connection_incomplete_description
                                        } else {
                                            R.string.mercadopago_pending_description
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (uiState.connectionIncomplete) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f, fill = false))
                        PrimaryButton(
                            text = stringResource(
                                if (uiState.connectionIncomplete) {
                                    R.string.mercadopago_retry
                                } else {
                                    R.string.mercadopago_connect_button
                                }
                            ),
                            onClick = onConnectClick,
                            enabled = !uiState.loading && !uiState.isConnecting,
                        )
                        OutlinedButton(
                            onClick = onContinueWithoutConnecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !uiState.loading,
                        ) {
                            Text(text = stringResource(
                                if (returnToProfile) R.string.mercadopago_return_profile
                                else R.string.mercadopago_continue_without_connecting,
                            ))
                        }
                    }
                }
                null -> Unit
            }
        }
    }
}
