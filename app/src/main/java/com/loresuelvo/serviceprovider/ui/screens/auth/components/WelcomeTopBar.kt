package com.loresuelvo.serviceprovider.ui.screens.auth.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.components.branding.AppLogo
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme

/**
 * Minimal top bar: brand logo + name on the left, secondary login
 * action on the right. Keeps authentication accessible for returning
 * users without competing with the primary value proposition.
 */
@Composable
fun WelcomeTopBar(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo(size = 36.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.brand_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onLoginClick) {
            Text(
                text = stringResource(R.string.welcome_login),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeTopBarPreview() {
    LoresuelvoTheme {
        WelcomeTopBar(onLoginClick = {})
    }
}