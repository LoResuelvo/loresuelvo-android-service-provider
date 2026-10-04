package com.loresuelvo.serviceprovider.ui.statistics

import android.content.res.Configuration
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderConversionScreen
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderConversionContrastTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `actual light theme keeps all conversion text and control pairs above 4 point 5`() = verify(false)
    @Test fun `actual dark theme keeps all conversion text and control pairs above 4 point 5`() = verify(true)

    private fun verify(dark: Boolean) {
        lateinit var colors: ColorScheme
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                LoresuelvoTheme {
                    colors = MaterialTheme.colorScheme
                    Surface(color = colors.background) {
                        ProviderConversionScreen(ProviderConversionUiState.Ready(conversionFixture()), true,
                            filters = ConversionFilters("2026-09-30", "2026-09-01", ConversionDateError.REVERSED),
                            periodExpanded = true)
                    }
                }
            }
        }
        compose.runOnIdle {
            assertContrast("body/background", colors.onSurface, colors.background)
            assertContrast("card body/surface", colors.onSurface, colors.surface)
            assertContrast("date input/surfaceContainer", colors.onSurface, colors.surfaceContainer)
            assertContrast("date hints/surfaceContainer", colors.onSurfaceVariant, colors.surfaceContainer)
            assertContrast("hero/composited background", colors.onPrimaryContainer, colors.primaryContainer.compositeOver(colors.background))
            assertContrast("hero/composited surface", colors.onPrimaryContainer, colors.primaryContainer.compositeOver(colors.surface))
            assertContrast("inline error/surfaceContainer", colors.error, colors.surfaceContainer)
            assertContrast("error field label/surfaceContainer", colors.error, colors.surfaceContainer)
            assertContrast("period control/surfaceContainer", colors.primary, colors.surfaceContainer)
            assertContrast("back and outlined control/background", colors.secondary, colors.background)
            assertContrast("apply label/primary", colors.onPrimary, colors.primary)
        }
    }
    private fun assertContrast(pair: String, foreground: Color, background: Color) {
        val fg = foreground.compositeOver(background).luminance()
        val bg = background.luminance()
        val ratio = (maxOf(fg, bg) + .05f) / (minOf(fg, bg) + .05f)
        assertTrue("Actual theme $pair contrast $ratio must be at least 4.5", ratio >= 4.5f)
    }
}
