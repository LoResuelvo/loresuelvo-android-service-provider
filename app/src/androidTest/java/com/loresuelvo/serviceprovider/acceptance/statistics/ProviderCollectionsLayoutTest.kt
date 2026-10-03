package com.loresuelvo.serviceprovider.acceptance.statistics

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
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
        var contrast = 0.0
        compose.setContent {
            val configuration = android.content.res.Configuration(androidx.compose.ui.platform.LocalConfiguration.current)
            configuration.uiMode = if (night.value) android.content.res.Configuration.UI_MODE_NIGHT_YES
                else android.content.res.Configuration.UI_MODE_NIGHT_NO
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f),
                androidx.compose.ui.platform.LocalConfiguration provides configuration) {
                LoresuelvoTheme {
                    val colors = MaterialTheme.colorScheme
                    contrast = (maxOf(colors.onSurface.luminance(), colors.surfaceContainer.luminance()) + .05) /
                        (minOf(colors.onSurface.luminance(), colors.surfaceContainer.luminance()) + .05)
                    Box(Modifier.requiredWidth(280.dp)) {
                        ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(result), {})
                    }
                }
            }
        }
        listOf(false, true).forEach { dark ->
            compose.runOnIdle { night.value = dark }
            listOf(R.string.collections_verified_total, R.string.collections_deposits, R.string.collections_balances,
                R.string.collections_money_note, R.string.collections_evolution_note,
                R.string.collections_filter_balances).forEach { id ->
                val label = context.getString(id)
                compose.onNodeWithTag("provider_collections").performScrollToNode(hasText(label))
                val node = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
                val bounds = node.fetchSemanticsNode().boundsInRoot
                val viewport = compose.onNodeWithTag("provider_collections").fetchSemanticsNode().boundsInRoot
                assertTrue("Text must fit at 200% font scale", bounds.left >= viewport.left && bounds.right <= viewport.right)
            }
            assertTrue("Actual collection card text requires at least 4.5:1 contrast", contrast >= 4.5)
            compose.onNodeWithText(context.getString(R.string.collections_section)).assertIsSelected().assertHasClickAction()
        }
    }
}
