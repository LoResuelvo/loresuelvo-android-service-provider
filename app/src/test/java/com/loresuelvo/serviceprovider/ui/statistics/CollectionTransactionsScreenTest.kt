package com.loresuelvo.serviceprovider.ui.statistics

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderCollectionsScreen
import com.loresuelvo.serviceprovider.ui.screens.statistics.formatActivityMoney
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
class CollectionTransactionsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val summary = collectionsFixture()
    private fun page(): CollectionTransactions = runBlocking {
        (TransactionsTestRepository().getTransactions(CollectionTransactionsQuery(summary.period.from, summary.period.to))
            as CollectionTransactionsOutcome.Success).page
    }
    @Test fun `transaction rows show date purpose exact money proposal optional order whole totals and filter events`() {
        val first = page()
        val withOrder = first.transactions.single().copy(id = 2, purpose = CollectionPurpose.SERVICE_BALANCE,
            serviceProposalId = 9, workOrderId = 10)
        var selected: CollectionPurpose? = null
        var loads = 0
        compose.setContent { LoresuelvoTheme {
            ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(summary), {},
                transactions = CollectionTransactionsUiState(page = first.copy(transactions = first.transactions + withOrder)),
                onPurpose = { selected = it }, onLoadMore = { loads++ })
        } }
        scroll(hasText("Movimientos del filtro: 3 · Total: ${formatActivityMoney(450000)}"))
        scroll(hasText("Propuesta #8") and hasText("Señas") and hasText(formatActivityMoney(120000)))
        compose.onNode(hasText("Propuesta #8")).assertTextContains("Verificado:", substring = true)
        scroll(hasText("Propuesta #9") and hasText("Orden #10") and hasText("Saldos"))
        scroll(hasText("Cargar más")); compose.onNodeWithText("Cargar más").performClick(); assertEquals(1, loads)
        scroll(hasText("Movimientos verificados"))
        compose.onNodeWithText("Todos").assertIsSelected()
        compose.onNode(hasText("Señas") and hasClickAction()).performClick()
        assertEquals(CollectionPurpose.BOOKING_DEPOSIT, selected)
    }
    @Test fun `continuation loading disables duplicate loads and invalid cursor explains explicit restart`() {
        val state = mutableStateOf(CollectionTransactionsUiState(page = page(), loading = true))
        var retries = 0
        compose.setContent { LoresuelvoTheme {
            ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(summary), {},
                transactions = state.value, onTransactionsRetry = { retries++ })
        } }
        scroll(hasText("Cargar más")); compose.onNodeWithText("Cargar más").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(loading = false,
            failure = CollectionsOutcome.Failure.InvalidQuery, restartRequired = true) }
        scroll(hasText("La continuación dejó de ser válida. Reintentá para consultar el filtro desde el inicio."))
        compose.onNodeWithText("Cargar más").assertDoesNotExist()
        scroll(hasText("Reintentar")); compose.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
        scroll(hasText("Total verificado") and hasText(formatActivityMoney(400000)))
    }
    private fun scroll(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
        compose.onNode(matcher).assertExists()
    }
}
