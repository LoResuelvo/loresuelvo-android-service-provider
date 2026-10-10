package com.loresuelvo.serviceprovider.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandSecondary, onPrimary = TextWhite,
    primaryContainer = BrandPrimary, onPrimaryContainer = TextWhite,
    secondary = BrandSecondary, onSecondary = TextWhite,
    secondaryContainer = Color(0xFF8CF5E4), onSecondaryContainer = Color(0xFF005047),
    tertiary = Color(0xFF755900), onTertiary = TextWhite,
    tertiaryContainer = BrandTertiary, onTertiaryContainer = BrandPrimary,
    background = BrandNeutral, onBackground = BrandPrimary,
    surface = SurfaceWhite, onSurface = BrandPrimary,
    surfaceVariant = Color(0xFFEFF3F2), onSurfaceVariant = SubtitleGray,
    surfaceDim = Color(0xFFE5E3DF), surfaceBright = SurfaceWhite,
    surfaceContainerLowest = SurfaceWhite, surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = SurfaceWhite, surfaceContainerHigh = Color(0xFFEFF3F2),
    surfaceContainerHighest = Color(0xFFE5ECE9), surfaceTint = BrandSecondary,
    outline = Color(0xFF737B83), outlineVariant = DividerGray,
    inverseSurface = BrandPrimary, inverseOnSurface = TextWhite, inversePrimary = Color(0xFF8CF5E4),
    error = Color(0xFFB91C1C), onError = TextWhite,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF690005),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CF5E4), onPrimary = BrandPrimary,
    primaryContainer = BrandPrimary, onPrimaryContainer = TextWhite,
    secondary = Color(0xFF8CF5E4), onSecondary = BrandPrimary,
    secondaryContainer = Color(0xFF183F3E), onSecondaryContainer = Color(0xFF8CF5E4),
    tertiary = BrandTertiary, onTertiary = BrandPrimary,
    tertiaryContainer = Color(0xFF584400), onTertiaryContainer = BrandTertiary,
    background = Color(0xFF101C2B), onBackground = Color(0xFFF4F7FA),
    surface = Color(0xFF1A2B48), onSurface = Color(0xFFF4F7FA),
    surfaceVariant = Color(0xFF26394F), onSurfaceVariant = Color(0xFFC5C6CE),
    surfaceDim = Color(0xFF101C2B), surfaceBright = Color(0xFF34465B),
    surfaceContainerLowest = Color(0xFF0B1624), surfaceContainerLow = Color(0xFF152337),
    surfaceContainer = Color(0xFF1A2B48), surfaceContainerHigh = Color(0xFF26394F),
    surfaceContainerHighest = Color(0xFF34465B), surfaceTint = Color(0xFF8CF5E4),
    outline = Color(0xFF94A3B8), outlineVariant = Color(0xFF42516A),
    inverseSurface = Color(0xFFF4F7FA), inverseOnSurface = BrandPrimary, inversePrimary = BrandSecondary,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val ProviderTypography = Typography().let { base -> base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
) }

@Composable
fun LoresuelvoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = ProviderTypography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content,
    )
}
