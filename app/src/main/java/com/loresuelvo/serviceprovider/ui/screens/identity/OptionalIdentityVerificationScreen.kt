package com.loresuelvo.serviceprovider.ui.screens.identity

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.platform.identity.IdentityVerificationLauncher
import com.loresuelvo.serviceprovider.ui.components.buttons.PrimaryButton
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationEffect
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationUiState
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationViewModel
import com.loresuelvo.serviceprovider.ui.navigation.Route

@Composable
fun OptionalIdentityVerificationRoute(
    navController: NavHostController,
    launcher: IdentityVerificationLauncher,
    viewModel: OptionalIdentityVerificationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, launcher) {
        launcher.attach(activity)
        onDispose { launcher.detach(activity) }
    }
    LaunchedEffect(viewModel, navController) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OptionalIdentityVerificationEffect.NavigateToMercadoPago -> {
                    navController.navigate(Route.MercadoPagoConnect.path) {
                        popUpTo(Route.OptionalIdentityVerification.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                OptionalIdentityVerificationEffect.NavigateToWelcome -> {
                    navController.navigate(Route.Welcome.path) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is OptionalIdentityVerificationEffect.LaunchVerification -> {
                    launcher.launch(effect.credential, viewModel::onVerificationResult)
                }
            }
        }
    }

    OptionalIdentityVerificationScreen(
        uiState = uiState,
        onVerifyNow = viewModel::verifyNow,
        onLater = viewModel::later,
    )
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("Identity verification requires an Activity context")
}

@Composable
fun OptionalIdentityVerificationScreen(
    uiState: OptionalIdentityVerificationUiState = OptionalIdentityVerificationUiState(),
    onVerifyNow: () -> Unit = {},
    onLater: () -> Unit = {},
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
                text = stringResource(R.string.identity_optional_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.identity_optional_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (uiState.loading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    CircularProgressIndicator()
                    Text(text = stringResource(R.string.identity_starting_verification))
                }
            }
            PrimaryButton(
                text = stringResource(R.string.identity_verify_now),
                onClick = onVerifyNow,
                enabled = !uiState.loading,
            )
            OutlinedButton(
                onClick = onLater,
                enabled = !uiState.loading,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(text = stringResource(R.string.identity_verify_later))
            }
        }
    }
}
