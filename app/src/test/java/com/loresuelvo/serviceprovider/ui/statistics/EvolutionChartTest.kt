package com.loresuelvo.serviceprovider.ui.statistics

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.ui.screens.statistics.*
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR-w360dp-h900dp-mdpi", sdk = [34])
class EvolutionChartTest {
    @get:Rule val compose = createComposeRule()
    private val from = Instant.parse("2026-10-01T03:00:00Z")
    private val points = listOf(EvolutionPoint(from, from.plusSeconds(86400), listOf(8, 5, 3)),
        EvolutionPoint(from.plusSeconds(86400), from.plusSeconds(172800), listOf(0, 0, 0)))
    private val labels = listOf(R.string.activity_bookings, R.string.activity_completions, R.string.activity_paid)

    @Test fun `arrows expose exact zero intervals and preserve bounded selection`() {
        var selected by mutableIntStateOf(0)
        compose.setContent { LoresuelvoTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                EvolutionChart(R.string.activity_evolution, points, labels, false, selected) { selected = it }
            }
        } }
        compose.onNodeWithContentDescription("Intervalo anterior").assertIsNotEnabled()
        compose.onNode(hasText("Contrataciones confirmadas") and hasText("8")).assertExists()
        compose.onNodeWithContentDescription("Intervalo siguiente").performScrollTo().performClick()
        assertEquals(1, selected)
        compose.onNodeWithContentDescription("Intervalo siguiente").assertIsNotEnabled()
        listOf("Contrataciones confirmadas", "Trabajos finalizados", "Trabajos pagados").forEach {
            compose.onNode(hasText(it) and hasText("0")).assertExists()
        }
        compose.onNodeWithContentDescription("Intervalo anterior").performClick()
        assertEquals(0, selected)
        compose.onNode(hasText("Contrataciones confirmadas") and hasText("8")).assertExists()
    }

    @Test fun `tapping the plot selects the interval and long amounts keep exact cents`() {
        var selected by mutableIntStateOf(0)
        val amount = 9007199254740993L
        val data = points.mapIndexed { index, point -> point.copy(values = listOf(0, 0, if (index == 1) amount else 0)) }
        compose.setContent { LoresuelvoTheme {
            EvolutionChart(R.string.collections_evolution, data,
                listOf(R.string.collections_deposits, R.string.collections_balances, R.string.collections_verified_total),
                true, selected) { selected = it }
        } }
        compose.onNodeWithTag("evolution_plot").performTouchInput { click(centerRight) }
        assertEquals(1, selected)
        compose.onNode(hasText("Total verificado") and hasText(formatActivityMoney(amount))).assertExists()
    }

    @Test fun `empty data does not manufacture an interval`() {
        compose.setContent { LoresuelvoTheme { EvolutionChart(R.string.activity_evolution, emptyList(), labels, false, 50, {}) } }
        compose.onNodeWithText("No hay intervalos para mostrar.").assertExists()
        compose.onNodeWithTag("evolution_interval").assertDoesNotExist()
    }
}
