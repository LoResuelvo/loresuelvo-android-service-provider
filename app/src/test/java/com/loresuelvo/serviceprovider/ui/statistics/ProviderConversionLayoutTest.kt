package com.loresuelvo.serviceprovider.ui.statistics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderConversionScreen
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "es-w390dp-h1300dp-mdpi")
class ProviderConversionLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var renderView: View

    @Test fun `conversion stages and requests render in actual light theme`() {
        compose.setContent {
            renderView = LocalView.current
            LoresuelvoTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                ProviderConversionScreen(ProviderConversionUiState.Ready(conversionFixture()), true,
                    listState = androidx.compose.runtime.remember { deterministicLazyListState() })
            } }
        }
        saveRender("conversion-light")
        textFits("Se convirtieron en contrataciones")
        textFits("12 de 20 propuestas")
        textFits("Finalizaciones informadas")
        textFits("88,89 %")
        saveRender("conversion-light-advances")
        textFits("3 de 5 solicitudes recibidas")
        saveRender("conversion-light-requests")
    }
    @Test
    @Config(qualifiers = "es-w320dp-h800dp-night-mdpi")
    fun `compact dark large text rtl keeps complete labels and numeric direction`() {
        compose.setContent {
            renderView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                LoresuelvoTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ProviderConversionScreen(ProviderConversionUiState.Ready(conversionFixture()), true,
                    listState = androidx.compose.runtime.remember { deterministicLazyListState() })
                } }
            }
        }
        textFits("Conversión de propuestas")
        textFits("Desde 4 sep. 2026, 09:00 (incluido) hasta 4 oct. 2026, 09:00 (excluido)", contentLtr = true)
        textFits("12 de 20 propuestas", contentLtr = true)
        saveRender("conversion-dark-large-type-rtl")
        textFits("Finalizaciones informadas")
        textFits("88,89 %", contentLtr = true)
        textFits("8 de 9 propuestas", contentLtr = true)
        saveRender("conversion-dark-large-type-rtl-advances")
        textFits("8 sin contratación observada", contentLtr = true)
        textFits("No significa que hayan sido rechazadas. Las propuestas todavía pueden avanzar.")
        textFits("Avance observado el ${com.loresuelvo.serviceprovider.ui.screens.statistics.formatActivityInstant(conversionFixture().observedAt)} · Buenos Aires. Incluye avances posteriores a la emisión, aunque ocurran fuera del período elegido.", contentLtr = true)
        textFits("3 de 5 solicitudes recibidas", contentLtr = true)
        textFits("Aceptar una solicitud abre la conversación; no confirma una contratación.", contentLtr = true)
        saveRender("conversion-dark-large-type-rtl-requests")
    }
    @Test
    @Config(qualifiers = "es-w320dp-h800dp-night-mdpi")
    fun `custom period controls and inline correction render at compact large rtl`() {
        compose.setContent {
            renderView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                LoresuelvoTheme { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ProviderConversionScreen(ProviderConversionUiState.Ready(conversionFixture()),
                        filters = ConversionFilters("2026-09-30", "2026-09-01", ConversionDateError.REVERSED),
                        periodExpanded = true,
                        listState = androidx.compose.runtime.remember { deterministicLazyListState() })
                } }
            }
        }
        textFits("Opciones del período")
        saveRender("conversion-dark-large-type-rtl-dates")
        textFits("La fecha inicial debe ser anterior o igual a la final.")
        saveRender("conversion-dark-large-type-rtl-date-error")
    }
    private fun textFits(text: String, contentLtr: Boolean = false) {
        compose.onNodeWithTag("provider_conversion").performScrollToNode(hasText(text))
        val results = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue("Missing text layout: $text", results.isNotEmpty())
        results.forEach { layout ->
            assertFalse("Vertical clipping: $text", layout.didOverflowHeight)
            (0 until layout.lineCount).forEach { line ->
                assertFalse("Ellipsized text: $text", layout.isLineEllipsized(line))
                // Native paragraphs retain maximum constraints even when Text measures to intrinsic width.
                // Check actual rendered line extents rather than the unused paragraph box.
                val left = layout.getLineLeft(line)
                val right = layout.getLineRight(line)
                assertTrue("Horizontal clipping: $text; line=$left..$right, size=${layout.size}",
                    left >= -0.01f && right <= layout.size.width + 0.01f)
            }
            if (contentLtr) assertEquals("Content direction: $text",
                ResolvedTextDirection.Ltr, layout.getParagraphDirection(0))
        }
    }
    private fun saveRender(name: String) {
        val file = File("build/reports/conversion-design/$name.png")
        file.parentFile.mkdirs()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(renderView.width, renderView.height, Bitmap.Config.ARGB_8888)
            renderView.draw(Canvas(bitmap))
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
