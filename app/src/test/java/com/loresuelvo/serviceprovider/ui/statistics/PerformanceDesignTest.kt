package com.loresuelvo.serviceprovider.ui.statistics

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.ui.screens.statistics.*
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR-w360dp-h800dp-mdpi", sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PerformanceDesignTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: android.view.View

    @Test fun `summary exposes conversion without scrolling`() {
        var opened = false
        render { ProviderActivityScreen(ProviderActivityUiState.Ready(activityFixture()), {}, filters = activityFilters(), onConversion = { opened = true }, onCollections = {}) }
        compose.onNodeWithText("Resumen").assertIsSelected()
        compose.onNodeWithText("Conversión").assertIsDisplayed().performClick()
        assertEquals(true, opened)
        save("summary")
        compose.onNodeWithTag("provider_activity").performScrollToNode(hasTestTag("evolution_plot"))
        compose.onNodeWithTag("evolution_plot").performScrollTo()
        save("activity-evolution")
    }

    @Test fun `embedded conversion exposes section navigation and summary`() {
        var section: PerformanceSection? = null
        var returned = false
        render { ProviderConversionScreen(ProviderConversionUiState.Ready(conversionFixture()),
            filters = ConversionFilters("2026-09-04", "2026-10-04"), onBack = { returned = true }, onSection = { section = it }) }
        compose.onNodeWithText("Conversión").assertIsSelected()
        compose.onNodeWithText("Resumen").performClick()
        assertEquals(true, returned)
        compose.onNodeWithText("Cobros").performClick()
        assertEquals(PerformanceSection.COLLECTIONS, section)
        save("conversion")
    }

    @Test fun `collections has distinct verified and outstanding amounts`() {
        render { ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(collectionsFixture()), {}, filters = activityFilters()) }
        compose.onNodeWithText("Total verificado").assertIsDisplayed()
        save("collections")
        compose.onNodeWithTag("provider_collections").performScrollToNode(hasTestTag("evolution_plot"))
        compose.onNodeWithTag("evolution_plot").performScrollTo()
        save("collections-evolution")
    }

    @Test fun `reputation keeps rating and sample together`() {
        render { ProviderReputationScreen(ProviderReputationUiState.Ready(reputationFixture()), {}) }
        compose.onNodeWithText("4,63 / 5").assertIsDisplayed()
        compose.onNodeWithText("24 reseñas de trabajos pagados").assertIsDisplayed()
        save("reputation")
    }

    private fun activityFilters(): ActivityFilters {
        val period = activityFixture().period
        return ActivityFilters("2026-09-04", "2026-10-04", com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(period.from, period.to))
    }

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            view = LocalView.current
            LoresuelvoTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() } }
        }
    }
    private fun save(name: String) {
        compose.runOnIdle {
            val file = File("build/reports/performance-design/$name.png").apply { parentFile.mkdirs() }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
