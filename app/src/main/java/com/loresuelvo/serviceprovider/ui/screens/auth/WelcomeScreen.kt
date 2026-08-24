package com.loresuelvo.serviceprovider.ui.screens.auth

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.auth.WelcomeCategoriesUiState
import com.loresuelvo.serviceprovider.ui.auth.WelcomeError
import com.loresuelvo.serviceprovider.ui.components.buttons.GoogleButton
import com.loresuelvo.serviceprovider.ui.components.buttons.PrimaryButton
import com.loresuelvo.serviceprovider.ui.screens.auth.components.CategoryChipRow
import com.loresuelvo.serviceprovider.ui.screens.auth.components.HeroSection
import com.loresuelvo.serviceprovider.ui.screens.auth.components.HowItWorksStep
import com.loresuelvo.serviceprovider.ui.screens.auth.components.WelcomeScaffold
import com.loresuelvo.serviceprovider.ui.screens.auth.components.WelcomeTopBar
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.ui.theme.SubtitleGray

/**
 * Welcome screen for the provider app. Value-first layout: social
 * proof and the provider journey are communicated before any
 * authentication prompt, so the user understands what LoResuelvo is
 * within the first seconds.
 *
 * Stateless: every visible value comes from resources and every user
 * action is delegated to a callback. The host
 * ([com.loresuelvo.serviceprovider.ui.navigation.WelcomeRoute])
 * wires these callbacks to
 * [com.loresuelvo.serviceprovider.ui.auth.WelcomeViewModel].
 */
@Composable
fun WelcomeScreen(
    error: WelcomeError? = null,
    categories: WelcomeCategoriesUiState = WelcomeCategoriesUiState.Loading,
    onRegisterClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onGoogleClick: () -> Unit = {},
) {
    val errorMessage = error?.let { welcomeErrorMessage(it) }
    WelcomeScaffold {
        WelcomeTopBar(onLoginClick = onLoginClick)

        Spacer(Modifier.height(24.dp))

        HeroSection()

        Spacer(Modifier.height(28.dp))

        HowItWorksStep(
            number = 1,
            title = stringResource(R.string.welcome_step1_title),
            description = stringResource(R.string.welcome_step1_description),
        )
        Spacer(Modifier.height(14.dp))
        HowItWorksStep(
            number = 2,
            title = stringResource(R.string.welcome_step2_title),
            description = stringResource(R.string.welcome_step2_description),
        )
        Spacer(Modifier.height(14.dp))
        HowItWorksStep(
            number = 3,
            title = stringResource(R.string.welcome_step3_title),
            description = stringResource(R.string.welcome_step3_description),
        )

        Spacer(Modifier.height(24.dp))

        CategorySection(state = categories)

        Spacer(Modifier.height(28.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }

        PrimaryButton(
            text = stringResource(R.string.welcome_register),
            onClick = onRegisterClick,
        )

        Spacer(Modifier.height(14.dp))

        GoogleButton(
            text = stringResource(R.string.welcome_google),
            onClick = onGoogleClick,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.welcome_legal),
            style = MaterialTheme.typography.bodySmall,
            color = SubtitleGray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun welcomeErrorMessage(error: WelcomeError): String = when (error) {
    WelcomeError.Authentication -> stringResource(R.string.welcome_auth_error)
    WelcomeError.Network -> stringResource(R.string.welcome_auth_network_error)
    is WelcomeError.Server -> stringResource(R.string.welcome_auth_server_error, error.code)
    WelcomeError.Unauthorized -> stringResource(R.string.welcome_auth_unauthorized_error)
}

/**
 * Renders the illustrative service-category chips based on the
 * [WelcomeCategoriesUiState]: a slim progress indicator while
 * loading, the scrollable chip row when ready, or an error message
 * when the fetch fails or returns nothing.
 */
@Composable
private fun CategorySection(state: WelcomeCategoriesUiState) {
    when (state) {
        WelcomeCategoriesUiState.Loading ->
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )

        is WelcomeCategoriesUiState.Ready ->
            CategoryChipRow(categories = state.categories.map { it.name })

        WelcomeCategoriesUiState.Error ->
            Text(
                text = stringResource(R.string.welcome_categories_error),
                style = MaterialTheme.typography.bodySmall,
                color = SubtitleGray,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth(),
            )
    }
}

@Preview(showBackground = true, name = "Welcome · phone")
@Preview(showBackground = true, name = "Welcome · small", heightDp = 640, widthDp = 320)
@Composable
private fun WelcomeScreenPreview() {
    LoresuelvoTheme {
        WelcomeScreen(
            categories = WelcomeCategoriesUiState.Ready(
                categories = listOf(
                    com.loresuelvo.serviceprovider.domain.category.Category(1, "Plomería"),
                    com.loresuelvo.serviceprovider.domain.category.Category(2, "Electricidad"),
                    com.loresuelvo.serviceprovider.domain.category.Category(3, "Pintura"),
                ),
            ),
        )
    }
}