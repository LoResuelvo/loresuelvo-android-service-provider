package com.loresuelvo.serviceprovider.ui.screens.identity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.components.buttons.PrimaryButton
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationEffect
import com.loresuelvo.serviceprovider.ui.identity.OptionalIdentityVerificationViewModel
import com.loresuelvo.serviceprovider.ui.navigation.Route

@Composable
fun OptionalIdentityVerificationRoute(
    navController: NavHostController,
    viewModel: OptionalIdentityVerificationViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel, navController) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OptionalIdentityVerificationEffect.NavigateToMercadoPago -> {
                    navController.navigate(Route.MercadoPagoConnect.path) {
                        popUpTo(Route.OptionalIdentityVerification.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    OptionalIdentityVerificationScreen(
        onVerifyNow = viewModel::verifyNow,
        onLater = viewModel::later,
    )
}

@Composable
fun OptionalIdentityVerificationScreen(
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
            PrimaryButton(
                text = stringResource(R.string.identity_verify_now),
                onClick = onVerifyNow,
            )
            OutlinedButton(
                onClick = onLater,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(text = stringResource(R.string.identity_verify_later))
            }
        }
    }
}
