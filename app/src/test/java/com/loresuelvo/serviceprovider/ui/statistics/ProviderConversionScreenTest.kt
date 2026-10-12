package com.loresuelvo.serviceprovider.ui.statistics

import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.domain.statistics.ConversionOutcome
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderConversionUseCase
import com.loresuelvo.serviceprovider.ui.screens.statistics.*
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class ProviderConversionScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun reveal(text: String) {
        compose.onNodeWithTag("provider_conversion").performScrollToNode(hasText(text))
        compose.onAllNodesWithText(text).onFirst().assertIsDisplayed()
    }
    private fun productionResult(issued: Long = 20, empty: Boolean = false): ProviderConversionUiState.Ready {
        val repository = ConversionTestRepository().apply { outcome = ConversionOutcome.Success(conversionFixture(issued, empty)) }
        return ProviderConversionUiState.Ready((runBlocking { GetProviderConversionUseCase(repository, java.time.Clock.fixed(java.time.Instant.parse("2026-10-04T12:00:00Z"), java.time.ZoneOffset.UTC))(com.loresuelvo.serviceprovider.domain.statistics.ConversionQuery()) }
            as ConversionOutcome.Success).conversion)
    }
    @Test fun `production cohort displays supplied stages expandable advances neutral status and distinct requests`() {
        val state = productionResult(); val expanded = mutableStateOf(false)
        compose.setContent { LoresuelvoTheme { Surface { ProviderConversionScreen(state, expanded.value, { expanded.value = it }) } } }
        reveal("de tus propuestas se convirtieron en contrataciones"); reveal("12 de 20 propuestas")
        reveal("Seguimiento de las 20 propuestas enviadas en el período."); compose.onNodeWithText("100 % de las propuestas enviadas").assertDoesNotExist(); reveal("12 propuestas contratadas")
        reveal("9 trabajos finalizados"); reveal("45 % de las propuestas enviadas"); reveal("8 trabajos pagados por completo"); reveal("40 % de las propuestas enviadas")
        reveal("Ver avance entre etapas"); compose.onNodeWithText("Ver avance entre etapas").performClick()
        reveal("De contratadas a finalizadas"); reveal("75 %"); reveal("9 de 12 propuestas")
        reveal("De finalizadas a pagadas"); reveal("88,89 %"); reveal("8 de 9 propuestas")
        reveal("8 propuestas todavía sin contratar")
        reveal("Todavía pueden avanzar; no se consideran rechazadas.")
        reveal("Incluye avances posteriores al período elegido.")
        reveal("Solicitudes de trabajo que aceptaste"); reveal("5 solicitudes recibidas"); reveal("3 solicitudes aceptadas"); reveal("2 solicitudes por responder")
        reveal("3 de 5 solicitudes recibidas")
        reveal("De las solicitudes de trabajo que recibiste en el período elegido:")
    }
    @Test fun `no proposals retains nonzero requests and exposes unavailable bases`() {
        compose.setContent { LoresuelvoTheme { Surface { ProviderConversionScreen(productionResult(0, true), true) } } }
        reveal("Sin propuestas en este período. Las solicitudes se muestran por separado.")
        reveal("Seguimiento de las 0 propuestas enviadas en el período."); reveal("No disponible"); reveal("0 de 0 propuestas")
        reveal("De contratadas a finalizadas"); reveal("De finalizadas a pagadas")
        reveal("Solicitudes de trabajo que aceptaste"); reveal("5 solicitudes recibidas"); reveal("3 solicitudes aceptadas"); reveal("3 de 5 solicitudes recibidas")
        compose.onNodeWithText("de tus propuestas se convirtieron en contrataciones").assertDoesNotExist()
    }
    @Test fun `issued proposals without milestones displays zero contraction and unavailable later advances`() {
        compose.setContent { LoresuelvoTheme { Surface { ProviderConversionScreen(productionResult(5, true), true) } } }
        reveal("de tus propuestas se convirtieron en contrataciones"); reveal("0 %"); reveal("0 de 5 propuestas")
        reveal("De contratadas a finalizadas"); reveal("No disponible")
        reveal("5 propuestas todavía sin contratar")
    }
    @Test fun `loading error retry expired session and back expose no invented metrics`() {
        val state = mutableStateOf<ProviderConversionUiState>(ProviderConversionUiState.Loading)
        var retries = 0; var backs = 0
        compose.setContent { LoresuelvoTheme { Surface { ProviderConversionScreen(state.value,
            onRetry = { retries++ }, onBack = { backs++ }) } } }
        reveal("Cargando la conversión…"); compose.onNodeWithText("Emitidas").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver a Actividad").performClick(); assertEquals(1, backs)
        compose.runOnIdle { state.value = ProviderConversionUiState.Error(ConversionOutcome.Failure.Network) }
        reveal("Reintentar"); compose.onNodeWithText("Reintentar").performClick(); assertEquals(1, retries)
        compose.onNodeWithText("0 %").assertDoesNotExist()
        compose.runOnIdle { state.value = ProviderConversionUiState.SessionExpired }
        compose.onNodeWithText("Reintentar").assertDoesNotExist(); compose.onNodeWithText("5 solicitudes recibidas").assertDoesNotExist()
    }

    @Test fun `independent date controls retain draft show inline correction and apply without Activity options`() {
        val filters = mutableStateOf(ConversionFilters("2026-09-01", "2026-09-30"))
        var applied = 0
        compose.setContent { LoresuelvoTheme { Surface {
            ProviderConversionScreen(productionResult(), filters = filters.value,
                onEditDates = { from, to -> filters.value = ConversionFilters(from, to) },
                onApplyDates = { applied++; filters.value = filters.value.copy(dateError = ConversionDateError.INCOMPLETE) },
                periodExpanded = true)
        } } }
        compose.onNodeWithTag("provider_conversion").performScrollToNode(hasTestTag("conversion_from_day"))
        compose.onNodeWithTag("conversion_from_day").performTextReplacement("")
        compose.onNodeWithTag("provider_conversion").performScrollToNode(hasTestTag("conversion_apply_period"))
        compose.onNodeWithTag("conversion_apply_period").performClick()
        assertEquals(1, applied)
        reveal("Completá las dos fechas para consultar el período.")
        compose.onNodeWithText("Agrupar por").assertDoesNotExist()
        compose.onNodeWithTag("provider_conversion").performScrollToNode(hasTestTag("conversion_from_day"))
        compose.onNodeWithTag("conversion_from_day").performTextReplacement("2026-09-02")
        assertNull(filters.value.dateError)
        reveal("de tus propuestas se convirtieron en contrataciones")
    }

    @Test fun `route waits for measured ready layout while expansion changes during metadata restoration`() {
        val clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-04T12:00:00Z"), java.time.ZoneOffset.UTC)
        val repository = ConversionTestRepository().apply { gate = kotlinx.coroutines.CompletableDeferred() }
        val saved = androidx.lifecycle.SavedStateHandle(mapOf(
            "conversion.from" to "2026-09-01T00:00:00-03:00", "conversion.to" to "2026-10-01T00:00:00-03:00",
            "conversion.fromDay" to "2026-09-01", "conversion.throughDay" to "2026-09-30",
            "conversion.expanded" to true, "conversion.readingIndex" to 8, "conversion.readingOffset" to 17))
        val vm = ProviderConversionViewModel(GetProviderConversionUseCase(repository, clock), ActivityTestSessionStore(), clock, saved)
        val store = androidx.lifecycle.ViewModelStore().apply { put("conversion", vm) }
        try {
            compose.setContent { LoresuelvoTheme { Surface {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        androidx.compose.ui.platform.LocalDensity.current.density, 2f)) {
                    ProviderConversionRoute(vm) {}
                }
            } } }
            compose.waitForIdle()
            assertEquals(ProviderConversionUiState.Loading, vm.uiState.value)
            assertEquals(8, vm.readingIndex)
            compose.runOnIdle { vm.expandAdvances(false); repository.gate!!.complete(Unit) }
            compose.waitForIdle()
            assertFalse(vm.advancesExpanded.value)
            assertEquals(8 to 17, compose.onNodeWithTag("provider_conversion").fetchSemanticsNode().config[CONVERSION_READING_POSITION])
            assertEquals(8, vm.readingIndex); assertEquals(17, vm.readingOffset)
        } finally { compose.runOnIdle { store.clear() } }
    }
}
