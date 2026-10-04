package com.loresuelvo.serviceprovider.acceptance.statistics

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.ReputationOutcome
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderReputationScreen
import com.loresuelvo.serviceprovider.ui.statistics.ProviderReputationUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderReputationLayoutAcceptanceTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun narrow_large_text_rtl_light_and_dark_preserve_labels_controls_and_readable_numbers() {
        val result = kotlinx.coroutines.runBlocking {
            (NavigationReputationRepository().getReputation(null) as ReputationOutcome.Success).reputation
        }
        val night = mutableStateOf(false)
        lateinit var colors: ColorScheme
        var pixelDensity = 1f
        compose.setContent {
            val configuration = android.content.res.Configuration(LocalConfiguration.current)
            configuration.uiMode = if (night.value) android.content.res.Configuration.UI_MODE_NIGHT_YES
                else android.content.res.Configuration.UI_MODE_NIGHT_NO
            val density = LocalDensity.current
            pixelDensity = density.density
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                LoresuelvoTheme {
                    colors = MaterialTheme.colorScheme
                    Box(Modifier.requiredWidth(280.dp)) {
                        ProviderReputationScreen(ProviderReputationUiState.Ready(result), {})
                    }
                }
            }
        }
        listOf(false, true).forEach { dark ->
            compose.runOnIdle { night.value = dark }
            readable(context.getString(R.string.reputation_lifetime))
            readable(context.getString(R.string.reputation_average, "4,63"), true)
            readable(context.getString(R.string.reputation_sample, 60L), true)
            (1..5).forEach { rating ->
                readable(context.getString(R.string.reputation_bucket, rating, result.ratingDistribution[rating - 1].count), true)
            }
            readable(context.getString(R.string.reputation_percentage, "80"), true)
            readable(context.getString(R.string.reputation_coverage_counts, 60L, 75L), true)
            readable(context.getString(R.string.reputation_work, 300), true)
            readable(context.getString(R.string.reputation_review_rating, 5), true, reviewId = 300)
            readable("Actual client comment for work 300", true)
            val more = control(R.string.reputation_load_more)
            val buttonBounds = more.fetchSemanticsNode().boundsInRoot
            assertTrue("Load more has a 48dp minimum touch target", buttonBounds.height >= 48 * pixelDensity - 1)
            listOf(R.string.activity_section, R.string.collections_section, R.string.reputation_section).forEach { label ->
                val tab = compose.onNodeWithText(context.getString(label)).performScrollTo().assertHasClickAction().assertIsDisplayed()
                assertTrue("Tabs have a 48dp minimum touch target", tab.fetchSemanticsNode().boundsInRoot.height >= 48 * pixelDensity - 1)
            }
            compose.onNodeWithText(context.getString(R.string.reputation_section)).assertIsSelected()
            control(R.string.reputation_refresh).assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(if (dark) Color(0xFF25303A) else com.loresuelvo.serviceprovider.ui.theme.SurfaceWhite, colors.surface)
                listOf(colors.secondary to colors.surface, colors.secondary to colors.background,
                    colors.onSurface to colors.surface, colors.onPrimary to colors.primary).forEach { (foreground, background) ->
                    assertTrue("Actual reputation text/control contrast in dark=$dark", contrast(foreground, background) >= 4.5)
                }
            }
        }
    }

    private fun control(id: Int): SemanticsNodeInteraction {
        val matcher = hasText(context.getString(id)) and hasClickAction()
        compose.onNodeWithTag("provider_reputation").performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun readable(text: String, ltr: Boolean = false, reviewId: Int? = null) {
        val row = reviewId?.let { hasTestTag("reputation_review_$it") }
        compose.onNodeWithTag("provider_reputation").performScrollToNode(row ?: hasText(text))
        val matcher = if (row == null) hasText(text) else hasText(text) and hasAnyAncestor(row)
        val node = compose.onNode(matcher, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("Text layout is exposed for $text", layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse("Text height is not clipped: $text", layout.didOverflowHeight)
            (0 until layout.lineCount).forEach { line ->
                assertFalse("Text is not ellipsized: $text", layout.isLineEllipsized(line))
                assertTrue("Text line fits its width: $text", layout.getLineLeft(line) >= -.01f &&
                    layout.getLineRight(line) <= layout.size.width + .01f)
            }
            if (ltr) assertEquals(ResolvedTextDirection.Ltr, layout.getParagraphDirection(0))
        }
    }
    private fun contrast(foreground: Color, background: Color): Double =
        (maxOf(foreground.luminance(), background.luminance()) + .05) /
            (minOf(foreground.luminance(), background.luminance()) + .05)
}
