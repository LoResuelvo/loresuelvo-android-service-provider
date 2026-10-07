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
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderReputationScreen
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
class ProviderReputationLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var renderView: View

    @Test fun `reputation follows theme and renders readable real review content`() {
        compose.setContent {
            renderView = LocalView.current
            LoresuelvoTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ProviderReputationScreen(ProviderReputationUiState.Ready(reputationFixture()), {},
                        listState = androidx.compose.runtime.remember { deterministicLazyListState() })
                }
            }
        }
        saveRender("reputation-light")
        textFits("Toda tu trayectoria")
        textFits("4,63 / 5")
        textFits("24 de 30 trabajos pagados recibieron una reseña.")
        textFits("Stored client comment")
    }
    @Test
    @Config(qualifiers = "es-w320dp-h800dp-night-mdpi")
    fun `compact dark large text rtl labels and comments wrap without truncation`() {
        val comment = "Comentario escrito por el cliente que ocupa varias líneas y mantiene todo su contenido visible."
        val fixture = reputationFixture().let { it.copy(reviews = listOf(it.reviews.first().copy(description = comment))) }
        compose.setContent {
            renderView = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                LoresuelvoTheme {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ProviderReputationScreen(ProviderReputationUiState.Ready(fixture), {},
                            listState = androidx.compose.runtime.remember { deterministicLazyListState() })
                    }
                }
            }
        }
        saveRender("reputation-dark-large-type-rtl")
        textFits("Toda tu trayectoria")
        textFits("4,63 / 5", contentLtr = true)
        textFits("24 reseñas de trabajos pagados", contentLtr = true)
        textFits("5 estrellas: 17 calificaciones", contentLtr = true)
        textFits("80 %", contentLtr = true)
        textFits("24 de 30 trabajos pagados recibieron una reseña.", contentLtr = true)
        textFits("Solo cuentan trabajos pagados. Un trabajo sin reseña no baja tu calificación.", contentLtr = true)
        saveRender("reputation-dark-large-type-rtl-coverage")
        textFits("Trabajo #184", contentLtr = true)
        textFits("5 de 5 estrellas", contentLtr = true)
        textFits(comment)
        textFits("Cargar más reseñas", contentLtr = true)
        textFits("Actualizar reputación", contentLtr = true)
        saveRender("reputation-dark-large-type-rtl-review")
        textFits("Reseñas ordenadas por número de trabajo, no por fecha.", contentLtr = true)
        textFits("Consultado el ${com.loresuelvo.serviceprovider.ui.screens.statistics.formatActivityInstant(fixture.calculatedAt)} · Buenos Aires",
            contentLtr = true)
        saveRender("reputation-dark-large-type-rtl-notes")
    }
    private fun textFits(text: String, contentLtr: Boolean = false) {
        compose.onNodeWithTag("provider_reputation").performScrollToNode(hasText(text))
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
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
        val file = File("build/reports/reputation-design/$name.png")
        file.parentFile.mkdirs()
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(renderView.width, renderView.height, Bitmap.Config.ARGB_8888)
            renderView.draw(Canvas(bitmap))
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
}
