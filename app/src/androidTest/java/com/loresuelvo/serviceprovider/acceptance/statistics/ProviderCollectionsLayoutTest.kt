package com.loresuelvo.serviceprovider.acceptance.statistics

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderCollectionsScreen
import com.loresuelvo.serviceprovider.ui.statistics.ProviderCollectionsUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.time.Instant
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderCollectionsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun large_text_compact_width_and_system_modes_keep_money_and_labels_readable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val query = ActivityQuery(Instant.parse("2026-09-03T12:00:00Z"), Instant.parse("2026-10-03T12:00:00Z"))
        val result = kotlinx.coroutines.runBlocking {
            (NavigationCollectionsRepository().getCollections(query) as CollectionsOutcome.Success).collections
        }
        val night = mutableStateOf(false)
        lateinit var actualColors: ColorScheme
        compose.setContent {
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
            configuration.uiMode = if (night.value) android.content.res.Configuration.UI_MODE_NIGHT_YES
                else android.content.res.Configuration.UI_MODE_NIGHT_NO
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f),
                androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                LoresuelvoTheme {
                    val colors = MaterialTheme.colorScheme
                    actualColors = colors
                    Box(Modifier.requiredWidth(280.dp)) {
                        ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(result), {})
                    }
                }
            }
        }
        listOf(false, true).forEach { dark ->
            compose.runOnIdle { night.value = dark }
            val evolution = hasText(context.getString(R.string.collections_evolution)) and hasClickAction()
            compose.onNodeWithTag("provider_collections").performScrollToNode(evolution)
            compose.onNode(evolution).performClick()
            listOf(R.string.collections_verified_total, R.string.collections_deposits, R.string.collections_balances,
                R.string.collections_money_note, R.string.collections_evolution_note,
                R.string.collections_filter_balances).forEach { id ->
                val label = context.getString(id)
                val matcher = if (id == R.string.collections_filter_balances) hasText(label) and hasClickAction()
                    else hasText(label)
                compose.onNodeWithTag("provider_collections").performScrollToNode(matcher)
                val node = compose.onNode(matcher).performScrollTo().assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                val viewport = compose.onNodeWithTag("provider_collections").fetchSemanticsNode().boundsInRoot
                assertTrue("Text must fit at 200% font scale", bounds.left >= viewport.left && bounds.right <= viewport.right)
            }
            compose.onNodeWithTag("provider_collections").performScrollToNode(evolution)
            compose.onNode(evolution).performClick()
            compose.runOnIdle {
                assertEquals(if (dark) Color(0xFF171D24) else com.loresuelvo.serviceprovider.ui.theme.BrandNeutral,
                    actualColors.background)
                assertEquals(if (dark) Color(0xFF25303A) else com.loresuelvo.serviceprovider.ui.theme.SurfaceWhite,
                    actualColors.surface)
                listOf(actualColors.onSurface to actualColors.surfaceContainer,
                    actualColors.onSurfaceVariant to actualColors.surface,
                    actualColors.onPrimary to actualColors.primary,
                    actualColors.onSecondaryContainer to actualColors.secondaryContainer,
                    actualColors.onError to actualColors.error).forEach { (foreground, background) ->
                    assertTrue("Actual text/control colors must meet 4.5:1 in mode dark=$dark",
                        contrast(foreground, background.compositeOver(actualColors.surface)) >= 4.5)
                }
            }
            compose.onNodeWithText(context.getString(R.string.collections_section)).assertIsSelected().assertHasClickAction()
        }
    }
    private fun contrast(foreground: Color, background: Color): Double =
        (maxOf(foreground.luminance(), background.luminance()) + .05) /
            (minOf(foreground.luminance(), background.luminance()) + .05)
}
