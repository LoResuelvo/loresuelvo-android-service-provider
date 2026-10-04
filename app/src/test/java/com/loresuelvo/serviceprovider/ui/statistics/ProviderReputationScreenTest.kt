package com.loresuelvo.serviceprovider.ui.statistics

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.domain.statistics.ReputationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderReputationUseCase
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
class ProviderReputationScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun reveal(text: String) {
        compose.onNodeWithTag("provider_reputation").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun productionResult(empty: Boolean = false, eligible: Long = 30): ProviderReputationUiState.Ready {
        val repository = ReputationTestRepository().apply {
            outcome = ReputationOutcome.Success(reputationFixture(eligible, empty))
        }
        val outcome = runBlocking { GetProviderReputationUseCase(repository)() } as ReputationOutcome.Success
        return ProviderReputationUiState.Ready(outcome.reputation)
    }
    @Test fun `real global summary explains lifetime paid coverage buckets actual comments and work number order`() {
        val state = productionResult()
        compose.setContent { LoresuelvoTheme { ProviderReputationScreen(state, {}) } }
        reveal("Toda tu trayectoria"); reveal("4,63 / 5"); reveal("24 reseñas de trabajos pagados")
        listOf(5 to 17, 4 to 5, 3 to 2, 2 to 0, 1 to 0).forEach { (rating, count) ->
            reveal("$rating estrellas: $count calificaciones")
        }
        reveal("80 %"); reveal("24 de 30 trabajos pagados recibieron una reseña.")
        reveal("Solo cuentan trabajos pagados. Un trabajo sin reseña no baja tu calificación.")
        reveal("Trabajo #184"); reveal("5 de 5 estrellas"); reveal("Stored client comment")
        reveal("Trabajo #179"); reveal("4 de 5 estrellas")
        compose.onNodeWithText("Sin comentario escrito").assertDoesNotExist()
        compose.onNodeWithText("Más recientes").assertDoesNotExist()
        reveal("Reseñas ordenadas por número de trabajo, no por fecha.")
        reveal("Consultado el ${formatActivityInstant(state.reputation.calculatedAt)} · Buenos Aires")
    }
    @Test fun `no reviews displays honest null rating and unavailable coverage before paid work`() {
        val state = productionResult(true, 0)
        compose.setContent { LoresuelvoTheme { ProviderReputationScreen(state, {}) } }
        reveal("Todavía sin calificaciones"); reveal("0 reseñas de trabajos pagados")
        (1..5).forEach { reveal("$it estrellas: 0 calificaciones") }
        reveal("No disponible"); reveal("0 de 0 trabajos pagados recibieron una reseña.")
        reveal("Aún no recibiste reseñas.")
        compose.onNodeWithText("Trabajo #184").assertDoesNotExist()
    }
    @Test fun `paid work without reviews displays zero percentage`() {
        val state = productionResult(true, 3)
        compose.setContent { LoresuelvoTheme { ProviderReputationScreen(state, {}) } }
        reveal("0 %"); reveal("0 de 3 trabajos pagados recibieron una reseña.")
    }
    @Test fun `error retry loading and expired session do not show invented aggregates`() {
        val state = mutableStateOf<ProviderReputationUiState>(ProviderReputationUiState.Error(ReputationOutcome.Failure.Network))
        var retries = 0
        compose.setContent { LoresuelvoTheme { ProviderReputationScreen(state.value, { retries++ }) } }
        compose.onNodeWithText("Reintentar").performClick(); assertEquals(1, retries)
        compose.onNodeWithText("Todavía sin calificaciones").assertDoesNotExist()
        compose.runOnIdle { state.value = ProviderReputationUiState.Loading }
        reveal("Cargando tu reputación…")
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
        compose.runOnIdle { state.value = ProviderReputationUiState.SessionExpired }
        compose.onNodeWithText("Stored client comment").assertDoesNotExist()
        compose.onNodeWithText("24 reseñas de trabajos pagados").assertDoesNotExist()
    }
    @Test fun `three tabs expose selection and route to existing activity and collections only`() {
        var selected: PerformanceSection? = null
        compose.setContent { LoresuelvoTheme { ProviderReputationScreen(ProviderReputationUiState.Loading, {}, { selected = it }) } }
        compose.onNodeWithText("Reputación").assertIsSelected()
        compose.onNodeWithText("Actividad").assertIsNotSelected().performClick()
        assertEquals(PerformanceSection.ACTIVITY, selected)
        compose.onNodeWithText("Cobros").assertIsNotSelected().performClick()
        assertEquals(PerformanceSection.COLLECTIONS, selected)
        compose.onNodeWithText("Conversión").assertDoesNotExist()
        compose.onNodeWithText("Opciones del período").assertDoesNotExist()
    }
    @Test fun `continuation controls preserve real rows and sticky tabs while error retry and refresh remain reachable`() {
        val state = mutableStateOf(ProviderReputationUiState.Ready(reputationFixture()))
        var more = 0; var retries = 0; var refreshes = 0
        compose.setContent { LoresuelvoTheme { ProviderReputationScreen(state.value, { retries++ },
            onLoadMore = { more++ }, onRefresh = { refreshes++ }) } }
        reveal("Cargar más reseñas"); compose.onNodeWithText("Cargar más reseñas").performClick()
        assertEquals(1, more)
        compose.onNodeWithText("Actividad").assertIsDisplayed()
        compose.onNodeWithText("Cobros").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(loading = true) }
        reveal("Cargando tu reputación…")
        compose.onNodeWithText("Cargar más reseñas").assertDoesNotExist()
        reveal("Trabajo #184")
        compose.runOnIdle { state.value = state.value.copy(loading = false, failure = ReputationOutcome.Failure.Network) }
        reveal("Reintentar"); compose.onNodeWithText("Reintentar").performClick(); assertEquals(1, retries)
        reveal("Trabajo #179")
        compose.runOnIdle { state.value = state.value.copy(restartRequired = true) }
        reveal("Consultar desde el principio")
        compose.onNodeWithText("Consultar desde el principio").performClick(); assertEquals(2, retries)
        reveal("Actualizar reputación"); compose.onNodeWithText("Actualizar reputación").performClick()
        assertEquals(1, refreshes)
        compose.runOnIdle { state.value = ProviderReputationUiState.Ready(reputationFixture().copy(nextCursor = null)) }
        compose.onNodeWithText("Cargar más reseñas").assertDoesNotExist()
    }

}
