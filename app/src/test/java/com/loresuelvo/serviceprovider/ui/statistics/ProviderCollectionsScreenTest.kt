package com.loresuelvo.serviceprovider.ui.statistics

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.domain.statistics.CollectionsOutcome
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderCollectionsUseCase
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
class ProviderCollectionsScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun `real use case summary explains fees bank balance and separately labelled current debts`() {
        val fixture = collectionsFixture()
        val result = runBlocking { GetProviderCollectionsUseCase(CollectionsTestRepository())(
            com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(fixture.period.from, fixture.period.to))
        } as CollectionsOutcome.Success
        compose.setContent { LoresuelvoTheme { ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(result.collections), {}) } }
        assertScrollableText("Total verificado", formatActivityMoney(400000))
        assertScrollableText("Importes en ARS, sin comisiones. Los cobros registrados no representan un saldo bancario.")
        assertScrollableText("Señas verificadas", formatActivityMoney(120000))
        assertScrollableText("Saldos verificados", formatActivityMoney(280000))
        assertScrollableText("Saldos actuales del servicio, sin filtro de período. No incluyen señas ya cobradas ni comisiones.")
        assertScrollableText("Trabajos programados", "Trabajos: 3")
        assertScrollableText("Finalizados con saldo pendiente", "Trabajos: 2")
    }
    @Test fun `retry loading empty and session expiry never present an error as zero money`() {
        val state = mutableStateOf<ProviderCollectionsUiState>(ProviderCollectionsUiState.Error(CollectionsOutcome.Failure.Network))
        var retries = 0
        compose.setContent { LoresuelvoTheme { ProviderCollectionsScreen(state.value, { retries++ }) } }
        compose.onNodeWithText("Reintentar").performClick(); assertEquals(1, retries)
        compose.onNodeWithText("No hubo cobros verificados durante este período.").assertDoesNotExist()
        compose.runOnIdle { state.value = ProviderCollectionsUiState.Loading }
        compose.onNodeWithText("Consultando tus cobros…").assertExists()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
        compose.runOnIdle { state.value = ProviderCollectionsUiState.Ready(collectionsFixture(empty = true)) }
        assertScrollableText("Total verificado", formatActivityMoney(0))
        assertScrollableText("No hubo cobros verificados durante este período.")
        assertScrollableText("Finalizados con saldo pendiente", "Trabajos: 2")
        compose.runOnIdle { state.value = ProviderCollectionsUiState.SessionExpired }
        compose.onNodeWithText("Ingresá nuevamente para consultar tus cobros.").assertExists()
        compose.onNodeWithText("Total verificado").assertDoesNotExist()
        compose.onNodeWithText("Pendientes actuales").assertDoesNotExist()
    }
    @Test fun `tabs announce selection and return to Activity without another destination`() {
        var selectedActivity = false
        compose.setContent { LoresuelvoTheme { ProviderCollectionsScreen(ProviderCollectionsUiState.Loading, {},
            onActivity = { selectedActivity = true }) } }
        compose.onNodeWithText("Cobros").assertIsSelected()
        compose.onNodeWithText("Actividad").assertIsNotSelected().performClick()
        assertTrue(selectedActivity)
    }
    @Test fun `Desempeno tab uses the applied Activity query and returns without resetting it`() {
        val sessions = ActivityTestSessionStore()
        val activityRepository = ActivityTestRepository().apply { respondToQuery = true }
        val collectionsRepository = CollectionsTestRepository()
        val store = androidx.lifecycle.ViewModelStore()
        val activity = ProviderActivityViewModel(
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase(activityRepository), sessions,
            java.time.Clock.fixed(java.time.Instant.parse("2026-10-03T12:00:00Z"), java.time.ZoneOffset.UTC))
            .also { store.put("activity", it) }
        val collections = ProviderCollectionsViewModel(GetProviderCollectionsUseCase(collectionsRepository), sessions)
            .also { store.put("collections", it) }
        val transactions = CollectionTransactionsViewModel(
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetCollectionTransactionsUseCase(TransactionsTestRepository()), sessions)
            .also { store.put("transactions", it) }
        try {
            compose.setContent { LoresuelvoTheme {
                com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderPerformanceRoute(activity, collections, transactions)
            } }
            compose.onNodeWithText("Opciones del período").performClick()
            compose.onNodeWithText("Desde (AAAA-MM-DD)").performTextReplacement("2026-08-01")
            compose.onNodeWithText("Hasta (día incluido)").performTextReplacement("2026-08-31")
            compose.onNodeWithText("Consultar período").performScrollTo().performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Cobros"))
            compose.onNodeWithText("Cobros").performClick()
            compose.onNodeWithTag("provider_collections").assertExists()
            compose.runOnIdle {
                assertEquals(activity.query, collectionsRepository.queries.single())
                assertEquals(java.time.Instant.parse("2026-08-01T03:00:00Z"), collections.query!!.from)
                assertEquals(java.time.Instant.parse("2026-09-01T03:00:00Z"), collections.query!!.to)
            }
            compose.onNodeWithText("Actividad").performClick()
            compose.onNodeWithTag("provider_activity").assertExists()
            compose.runOnIdle { assertEquals(collections.query, activity.query) }
            compose.onNodeWithText("Cobros").performClick()
            compose.runOnIdle { assertEquals(1, collectionsRepository.queries.size) }
        } finally { compose.runOnIdle { store.clear() } }
    }

    private fun assertScrollableText(text: String, paired: String? = null) {
        val matcher = if (paired == null) hasText(text) else hasText(text) and hasText(paired)
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
        compose.onNode(matcher).assertExists()
    }
    @Test fun `evolution labels deposits balances total and zero buckets with unavailable comparison percentage`() {
        val result = collectionsEvolutionFixture(com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(
            java.time.Instant.parse("2026-09-03T12:00:00Z"), java.time.Instant.parse("2026-10-03T12:00:00Z"),
            comparePrevious = true))
        compose.setContent { LoresuelvoTheme { ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(result), {}) } }
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val evolution = context.getString(com.loresuelvo.serviceprovider.R.string.collections_evolution)
        compose.onNodeWithTag("provider_collections").performScrollToNode(hasText(evolution) and hasClickAction())
        compose.onNode(hasText(evolution) and hasClickAction()).performClick()
        val zero = context.getString(com.loresuelvo.serviceprovider.R.string.collections_bucket_values,
            formatActivityMoney(0), formatActivityMoney(0), formatActivityMoney(0))
        compose.onNodeWithTag("provider_collections").performScrollToNode(hasText(zero))
        compose.onNodeWithText(zero).assertIsDisplayed()
        val unavailable = context.getString(com.loresuelvo.serviceprovider.R.string.activity_unavailable)
        val change = context.getString(com.loresuelvo.serviceprovider.R.string.activity_comparison_change,
            formatActivityMoney(400000), unavailable)
        compose.onNodeWithTag("provider_collections").performScrollToNode(hasText(change))
        compose.onNodeWithText(change).assertIsDisplayed()
    }

    @Test fun `a year of buckets stays collapsed until requested and collapse makes movements reachable again`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val from = java.time.Instant.parse("2025-10-03T12:00:00Z")
        val base = collectionsFixture(com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery(
            from, from.plusSeconds(365 * 86400L)))
        val buckets = (0L..364L).map { day ->
            com.loresuelvo.serviceprovider.domain.statistics.CollectionBucket(
                base.period.from.plusSeconds(day * 86400), base.period.from.plusSeconds((day + 1) * 86400),
                com.loresuelvo.serviceprovider.domain.statistics.CollectionAmounts(day * 100, 0, day * 100))
        }
        compose.setContent { LoresuelvoTheme {
            ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(base.copy(evolution = buckets)), {})
        } }
        val evolution = hasText(context.getString(com.loresuelvo.serviceprovider.R.string.collections_evolution)) and hasClickAction()
        val movements = hasText(context.getString(com.loresuelvo.serviceprovider.R.string.collections_transactions))
        val last = context.getString(com.loresuelvo.serviceprovider.R.string.collections_bucket_values,
            formatActivityMoney(36400), formatActivityMoney(0), formatActivityMoney(36400))
        fun reveal(matcher: SemanticsMatcher) = compose.onNodeWithTag("provider_collections").performScrollToNode(matcher)
        reveal(movements)
        assertTrue(compose.onNodeWithTag("provider_collections").fetchSemanticsNode().config[
            com.loresuelvo.serviceprovider.ui.screens.statistics.COLLECTIONS_READING_POSITION].first < 25)
        compose.onNodeWithText(last).assertDoesNotExist()
        reveal(evolution); compose.onNode(evolution).assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
            context.getString(com.loresuelvo.serviceprovider.R.string.activity_collapsed))).performClick()
        reveal(hasText(last)); compose.onNodeWithText(last).assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("provider_collections").fetchSemanticsNode().config[
            com.loresuelvo.serviceprovider.ui.screens.statistics.COLLECTIONS_READING_POSITION].first > 300)
        reveal(evolution); compose.onNode(evolution).performClick()
        reveal(movements); compose.onNode(movements).assertIsDisplayed()
        compose.onNodeWithText(last).assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("provider_collections").fetchSemanticsNode().config[
            com.loresuelvo.serviceprovider.ui.screens.statistics.COLLECTIONS_READING_POSITION].first < 25)
    }

}
