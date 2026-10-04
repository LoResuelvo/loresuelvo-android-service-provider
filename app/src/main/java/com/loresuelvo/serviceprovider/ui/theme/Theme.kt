package com.loresuelvo.serviceprovider.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Material 3 color scheme mapped to the LoResuelvo brand palette
 * ([Color.kt], mirrored from the web design system). Dark colors follow
 * the approved performance design and use opaque accessible containers.
 */
private val LoresuelvoColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = TextWhite,
    secondary = BrandSecondary,
    onSecondary = TextWhite,
    secondaryContainer = BrandSecondary.copy(alpha = 0.12f),
    onSecondaryContainer = BrandSecondary,
    primaryContainer = BrandSecondary.copy(alpha = 0.12f),
    // Text needs stronger contrast over the translucent green container in light mode.
    onPrimaryContainer = Color(0xFF116450),
    tertiary = BrandTertiary,
    onTertiary = BrandAccept,
    background = BrandNeutral,
    onBackground = BrandAccept,
    surface = SurfaceWhite,
    onSurface = BrandAccept,
    error = Color(0xFFB91C1C),
    onError = TextWhite,
)

private val LoresuelvoDarkColorScheme = darkColorScheme(
    primary = Color(0xFF75D6BA),
    onPrimary = Color(0xFF171D24),
    primaryContainer = Color(0xFF25303A),
    onPrimaryContainer = Color(0xFF75D6BA),
    secondary = Color(0xFF75D6BA),
    onSecondary = Color(0xFF171D24),
    secondaryContainer = Color(0xFF25303A),
    onSecondaryContainer = Color(0xFF75D6BA),
    tertiary = BrandTertiary,
    onTertiary = Color(0xFF171D24),
    background = Color(0xFF171D24),
    onBackground = Color(0xFFF0F4F8),
    surface = Color(0xFF25303A),
    onSurface = Color(0xFFF0F4F8),
    surfaceVariant = Color(0xFF25303A),
    onSurfaceVariant = Color(0xFFBDC8D2),
    surfaceContainer = Color(0xFF25303A),
    surfaceContainerLow = Color(0xFF171D24),
    surfaceContainerHigh = Color(0xFF25303A),
    surfaceContainerHighest = Color(0xFF25303A),
    outline = Color(0xFF45515E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/**
 * App-wide theme. Wrap the composition root
 * ([com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNav]) so
 * every `MaterialTheme.colorScheme` / `MaterialTheme.typography`
 * lookup resolves to the brand values instead of Material defaults.
 */
@Composable
fun LoresuelvoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) LoresuelvoDarkColorScheme else LoresuelvoColorScheme,
        content = content,
    )
}
