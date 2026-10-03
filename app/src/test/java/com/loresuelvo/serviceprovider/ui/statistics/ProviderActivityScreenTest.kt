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
    @Test fun `error retry loading and empty remain distinct`() {
        val state = mutableStateOf<ProviderActivityUiState>(ProviderActivityUiState.Error(ActivityOutcome.Failure.Network))
        var retries = 0
        compose.setContent { LoresuelvoTheme { ProviderActivityScreen(state.value) { retries++ } } }
        compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
        compose.onNodeWithText("No hubo actividad durante este período.").assertDoesNotExist()
        compose.runOnIdle { state.value = ProviderActivityUiState.Loading }
        compose.onNodeWithText("Consultando tu actividad…").assertExists()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
        compose.runOnIdle { state.value = ProviderActivityUiState.Ready(activityFixture(empty = true)) }
        compose.onNodeWithText("No hubo actividad durante este período.").performScrollTo().assertExists()
        compose.onNodeWithText("No disponible").performScrollTo().assertExists()
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
        compose.onNodeWithText("Valor pactado de trabajos finalizados").performScrollTo().assertExists()
        compose.onNodeWithText("Importes en ARS, sin comisiones. El valor pactado no indica cuánto cobraste.")
            .performScrollTo().assertExists()
        assertScrollableText("Pendientes actuales")
        assertScrollableText("Al momento de la consulta, sin filtro de período.")
    }

    private fun assertScrollableText(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertExists()
    }

    @Test fun `ARS cents remain exact beyond floating point integer precision`() {
        assertTrue(formatActivityMoney(9007199254740993L).contains("90.071.992.547.409,93"))
    }
}
