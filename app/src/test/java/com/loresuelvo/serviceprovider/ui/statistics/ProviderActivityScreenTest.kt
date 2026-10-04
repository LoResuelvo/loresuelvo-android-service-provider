package com.loresuelvo.serviceprovider.ui.statistics

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import com.loresuelvo.serviceprovider.domain.statistics.ActivityOutcome
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderActivityScreen
import com.loresuelvo.serviceprovider.ui.screens.statistics.formatActivityMoney
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class ProviderActivityScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `conversion entry is visible and invokes the detail integration callback`() {
        var opened = 0
        compose.setContent {
            LoresuelvoTheme {
                androidx.compose.material3.Surface {
                    ProviderActivityScreen(ProviderActivityUiState.Ready(activityFixture()), {},
                        onConversion = { opened++ })
                }
            }
        }
        compose.onNodeWithTag("provider_activity").performScrollToNode(hasText("Conversión de propuestas"))
        compose.onNodeWithText("Conversión de propuestas").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test fun `error retry loading and empty remain distinct`() {
        val state = mutableStateOf<ProviderActivityUiState>(ProviderActivityUiState.Error(ActivityOutcome.Failure.Network))
        var retries = 0
        compose.setContent { LoresuelvoTheme { ProviderActivityScreen(state.value, onRetry = { retries++ }) } }
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
        compose.onNodeWithText("No hubo actividad durante este período.").assertDoesNotExist()
        compose.runOnIdle { state.value = ProviderActivityUiState.Loading }
        compose.onNodeWithText("Consultando tu actividad…").assertExists()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
        compose.runOnIdle { state.value = ProviderActivityUiState.Ready(activityFixture(empty = true)) }
        compose.onNodeWithText("No hubo actividad durante este período.").performScrollTo().assertExists()
        assertScrollableText("No disponible")
        assertScrollableText("Al momento de la consulta, sin filtro de período.")
        assertScrollableText("Finalizados con saldo pendiente")
    }
    @Test fun `70_1 and 70_3 outcomes explain agreed value and current pending meaning`() {
        val repository = ActivityTestRepository()
        val expected = activityFixture()
        val outcome = kotlinx.coroutines.runBlocking {
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase(repository)(
                com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(expected.period.from, expected.period.to))
        } as ActivityOutcome.Success
        compose.setContent {
            LoresuelvoTheme { ProviderActivityScreen(ProviderActivityUiState.Ready(outcome.activity), {}) }
        }
        assertScrollableText("Valor pactado de trabajos finalizados")
        assertScrollableText("Importes en ARS, sin comisiones. El valor pactado no indica cuánto cobraste.")
        assertScrollableText("Pendientes actuales")
        assertScrollableText("Al momento de la consulta, sin filtro de período.")
    }


    @Test fun `period options expose date callbacks selected grouping and labelled comparison switch`() {
        val fixture = activityFixture()
        val query = com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(fixture.period.from, fixture.period.to)
        val filters = mutableStateOf(ActivityFilters("2026-09-03", "2026-10-03", query))
        var applied = false
        var selected: com.loresuelvo.serviceprovider.domain.statistics.ActivityGranularity? = null
        var compared = false
        compose.setContent { LoresuelvoTheme {
            ProviderActivityScreen(ProviderActivityUiState.Ready(fixture), {}, filters.value,
                onEditDates = { from, through -> filters.value = filters.value.copy(fromDay = from, throughDay = through) },
                onApplyDates = { applied = true }, onGranularity = { selected = it }, onComparison = { compared = it })
        } }
        compose.onNodeWithText("Opciones del período").performClick()
        compose.onNodeWithText("Desde (AAAA-MM-DD)").performTextReplacement("2026-08-01")
        compose.onNodeWithText("Hasta (día incluido)").performTextReplacement("2026-08-31")
        compose.onNodeWithText("Consultar período").performScrollTo().performClick()
        assertEquals("2026-08-01", filters.value.fromDay); assertEquals("2026-08-31", filters.value.throughDay)
        assertTrue(applied)
        compose.onNodeWithText("Día").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Semana").performScrollTo().performClick()
        assertEquals(com.loresuelvo.serviceprovider.domain.statistics.ActivityGranularity.WEEK, selected)
        compose.onNodeWithContentDescription("Comparar con el período anterior").performScrollTo().performClick()
        assertTrue(compared)
    }

    @Test fun `date correction messages preserve the visible last valid period`() {
        val filters = mutableStateOf(ActivityFilters("2026-09-03", "2026-10-03",
            com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(activityFixture().period.from, activityFixture().period.to)))
        compose.setContent { LoresuelvoTheme { ProviderActivityScreen(ProviderActivityUiState.Ready(activityFixture()), {}, filters.value) } }
        compose.onNodeWithText("Opciones del período").performClick()
        listOf(ActivityDateError.REVERSED to "La fecha inicial debe ser anterior o igual a la final.",
            ActivityDateError.TOO_LONG to "Elegí un período de hasta 365 días.",
            ActivityDateError.FUTURE to "La fecha final no puede ser posterior a hoy.",
            ActivityDateError.FORMAT to "Ingresá ambas fechas válidas en formato AAAA-MM-DD.").forEach { (error, text) ->
            compose.runOnIdle { filters.value = filters.value.copy(dateError = error) }
            compose.onNodeWithText(text).performScrollTo().assertExists()
            assertScrollableText("Período consultado")
        }
    }

    @Test fun `evolution values expose three named series and zero intervals for every grouping`() {
        val start = java.time.Instant.parse("2026-07-01T03:00:00Z")
        val end = java.time.Instant.parse("2026-10-01T03:00:00Z")
        val state = mutableStateOf<ProviderActivityUiState>(ProviderActivityUiState.Ready(activityForQuery(
            com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(start, end))))
        compose.setContent { LoresuelvoTheme { ProviderActivityScreen(state.value, {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ver valores de la evolución"))
        compose.onNodeWithText("Ver valores de la evolución").performClick()
        com.loresuelvo.serviceprovider.domain.statistics.ActivityGranularity.entries.forEach { grouping ->
            compose.runOnIdle { state.value = ProviderActivityUiState.Ready(activityForQuery(
                com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(start, end, grouping))) }
            val zeroText = "Contrataciones: 0 · Finalizaciones informadas: 0 · Pagados por completo: 0"
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(zeroText))
            compose.onAllNodesWithText(zeroText, substring = true).onFirst().assertExists()
        }
    }

    @Test fun `comparison displays differences and unavailable percentages without comparing pending`() {
        val original = activityFixture()
        val query = com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(original.period.from, original.period.to,
            comparePrevious = true)
        compose.setContent { LoresuelvoTheme { ProviderActivityScreen(ProviderActivityUiState.Ready(activityForQuery(query)), {}) } }
        assertScrollableText("Comparación con el período anterior")
        assertScrollableText("Actual: 8 · Anterior: 4")
        assertScrollableText("Diferencia: 4 · Variación: 100%")
        assertScrollableText("Diferencia: 3 · Variación: No disponible", "Pagados por completo")
        assertScrollableText("Diferencia: No disponible · Variación: No disponible")
        assertScrollableText("Períodos de igual duración. Los pendientes actuales no se comparan.")
        assertScrollableText("Al momento de la consulta, sin filtro de período.")
    }


    @Test fun `different valid period renders the new backend totals`() {
        val state = mutableStateOf<ProviderActivityUiState>(ProviderActivityUiState.Ready(activityFixture()))
        compose.setContent { LoresuelvoTheme { ProviderActivityScreen(state.value, {}) } }
        assertScrollableText("8", "Contrataciones confirmadas")
        val august = com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(
            java.time.Instant.parse("2026-08-01T03:00:00Z"), java.time.Instant.parse("2026-09-01T03:00:00Z"))
        compose.runOnIdle { state.value = ProviderActivityUiState.Ready(activityForQuery(august)) }
        assertScrollableText("3", "Contrataciones confirmadas")
        assertScrollableText("2", "Finalizaciones informadas")
        assertScrollableText("1", "Pagados por completo")
        assertScrollableText("3", "Contrataciones confirmadas")
        compose.onNode(hasText("Contrataciones confirmadas") and hasText("8")).assertDoesNotExist()
    }

    @Test fun `one short interval has visible chart points and accessible exact values`() {
        val query = com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(
            java.time.Instant.parse("2026-10-03T10:00:00Z"), java.time.Instant.parse("2026-10-03T12:00:00Z"))
        compose.setContent { LoresuelvoTheme { ProviderActivityScreen(ProviderActivityUiState.Ready(activityForQuery(query)), {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(
            "Un intervalo: contrataciones 8, finalizaciones informadas 5, pagos completos 3."))
        compose.onNodeWithContentDescription("Un intervalo: contrataciones 8, finalizaciones informadas 5, pagos completos 3.")
            .performScrollTo().assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ver valores de la evolución"))
        compose.onNodeWithText("Ver valores de la evolución").performClick()
        assertScrollableText("Contrataciones: 8 · Finalizaciones informadas: 5 · Pagados por completo: 3")
    }

    @Test fun `Spanish comparison formats decimals while zero bases remain unavailable`() {
        comparisonLocale("Diferencia: 1 · Variación: 14,29%", "Diferencia: 3 · Variación: No disponible", "Pagados por completo")
    }

    @Test
    @Config(qualifiers = "en-rUS", sdk = [34])
    fun `English comparison formats decimals while zero bases remain unavailable`() {
        comparisonLocale("Difference: 1 · Change: 14.29%", "Difference: 3 · Change: Not available", "Fully paid jobs")
    }

    @Test fun `session expiry removes all private values and prompts login`() {
        val state = mutableStateOf<ProviderActivityUiState>(ProviderActivityUiState.Ready(activityFixture()))
        compose.setContent { LoresuelvoTheme { ProviderActivityScreen(state.value, {}) } }
        compose.onNode(hasText("Contrataciones confirmadas") and hasText("8")).performScrollTo().assertExists()
        compose.runOnIdle { state.value = ProviderActivityUiState.SessionExpired }
        compose.onNodeWithText("Ingresá nuevamente para consultar tu actividad.").assertExists()
        compose.onNodeWithText("Contrataciones confirmadas").assertDoesNotExist()
        compose.onNodeWithText("Pendientes actuales").assertDoesNotExist()
    }

    private fun comparisonLocale(defined: String, undefined: String, paidLabel: String) {
        val original = activityFixture()
        val query = com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(original.period.from, original.period.to,
            comparePrevious = true)
        val base = activityForQuery(query)
        val previous = requireNotNull(base.comparison)
        val result = base.copy(comparison = previous.copy(results = previous.results.copy(confirmedBookings = 7),
            changes = previous.changes.copy(confirmedBookings =
                com.loresuelvo.serviceprovider.domain.statistics.ActivityChange(1, 14.29))))
        compose.setContent { LoresuelvoTheme { ProviderActivityScreen(ProviderActivityUiState.Ready(result), {}) } }
        assertScrollableText(defined)
        assertScrollableText(undefined, paidLabel)
    }

    private fun assertScrollableText(text: String, label: String? = null) {
        val matcher = if (label == null) hasText(text) else hasText(text) and hasText(label)
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
        compose.onNode(matcher).assertExists()
    }

    @Test fun `ARS cents remain exact beyond floating point integer precision`() {
        assertTrue(formatActivityMoney(9007199254740993L).contains("90.071.992.547.409,93"))
    }
}
