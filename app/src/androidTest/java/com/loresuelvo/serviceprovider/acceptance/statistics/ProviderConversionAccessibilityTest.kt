package com.loresuelvo.serviceprovider.acceptance.statistics

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.ConversionQuery
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderConversionScreen
import com.loresuelvo.serviceprovider.ui.statistics.ConversionDateError
import com.loresuelvo.serviceprovider.ui.statistics.ConversionFilters
import com.loresuelvo.serviceprovider.ui.statistics.ProviderConversionUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.time.OffsetDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderConversionAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun compact_light_large_text_rtl_keeps_dates_errors_and_controls_accessible() = render(false)
    @Test fun compact_dark_large_text_rtl_keeps_dates_errors_and_controls_accessible() = render(true)
    private fun render(dark: Boolean) {
        val conversion = runBlocking { NavigationConversionRepository().getConversion(ConversionQuery(
            OffsetDateTime.parse("2026-09-01T00:00:00-03:00"), OffsetDateTime.parse("2026-10-01T00:00:00-03:00"))) }
            as com.loresuelvo.serviceprovider.domain.statistics.ConversionOutcome.Success
        var pixelsPerDp = 1f
        compose.setContent {
            val density = LocalDensity.current
            pixelsPerDp = density.density
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                LoresuelvoTheme {
                    val colors = MaterialTheme.colorScheme
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
                    Surface(Modifier.requiredWidth(320.dp).fillMaxHeight(), color = colors.background) {
                        ProviderConversionScreen(ProviderConversionUiState.Ready(conversion.conversion), true,
                            filters = ConversionFilters("2026-09-30", "2026-09-01", ConversionDateError.REVERSED),
                            periodExpanded = true)
                    }
                }
            }
        }
        listOf("conversion_period_options", "conversion_from_day", "conversion_through_day", "conversion_apply_period").forEach { tag ->
            compose.onNodeWithTag("provider_conversion").performScrollToNode(hasTestTag(tag))
            val node = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            assertTrue("Control $tag has a 48dp touch target", node.boundsInRoot.height >= 48 * pixelsPerDp - 1)
        }
        listOf(R.string.activity_date_order_error, R.string.conversion_period_label,
            R.string.conversion_hero, R.string.conversion_reported, R.string.conversion_acceptance_note).forEach { id ->
            val label = context.getString(id)
            compose.onNodeWithTag("provider_conversion").performScrollToNode(hasText(label))
            val bounds = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("provider_conversion").fetchSemanticsNode().boundsInRoot
            assertTrue("Label fits compact RTL viewport: $label", bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1)
        }
    }
    private fun assertContrast(pair: String, foreground: Color, background: Color) {
        val fg = foreground.compositeOver(background).luminance()
        val bg = background.luminance()
        val ratio = (maxOf(fg, bg) + .05f) / (minOf(fg, bg) + .05f)
        assertTrue("Actual theme $pair contrast $ratio must be at least 4.5", ratio >= 4.5f)
    }
}
