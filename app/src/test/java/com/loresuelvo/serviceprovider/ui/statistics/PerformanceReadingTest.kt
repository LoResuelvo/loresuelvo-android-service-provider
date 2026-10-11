package com.loresuelvo.serviceprovider.ui.statistics

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.usecase.statistics.*
import com.loresuelvo.serviceprovider.ui.screens.statistics.COLLECTIONS_READING_POSITION
import com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderPerformanceRoute
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-w390dp-h800dp-mdpi")
class PerformanceReadingTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun close() { compose.runOnIdle { store.clear() } }

    @Test fun changing_transaction_purpose_keeps_the_filter_in_view_and_returning_does_not_fetch() {
        val sessions = ActivityTestSessionStore()
        val clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)
        val collectionRepository = CollectionsTestRepository()
        val transactionRepository = TransactionsTestRepository()
        lateinit var activity: ProviderActivityViewModel
        lateinit var collections: ProviderCollectionsViewModel
        lateinit var transactions: CollectionTransactionsViewModel
        lateinit var reputation: ProviderReputationViewModel
        compose.runOnIdle {
            activity = ProviderActivityViewModel(GetProviderActivityUseCase(ActivityTestRepository()), sessions, clock)
            collections = ProviderCollectionsViewModel(GetProviderCollectionsUseCase(collectionRepository), sessions)
            transactions = CollectionTransactionsViewModel(GetCollectionTransactionsUseCase(transactionRepository), sessions)
            reputation = ProviderReputationViewModel(GetProviderReputationUseCase(ReputationTestRepository()), sessions)
            store.put("activity", activity); store.put("collections", collections)
            store.put("transactions", transactions); store.put("reputation", reputation)
        }
        compose.setContent { LoresuelvoTheme { ProviderPerformanceRoute(activity, collections, transactions, reputation) } }
        compose.onNodeWithText("Cobros").performClick()
        compose.onNodeWithTag("provider_collections").performScrollToNode(hasText("Movimientos verificados"))
        listOf("Saldos", "Señas", "Todos").forEach { label ->
            compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNode(hasText(label) and hasClickAction()).assertIsDisplayed().assertIsSelected()
            val position = compose.onNodeWithTag("provider_collections").fetchSemanticsNode().config[COLLECTIONS_READING_POSITION]
            assertTrue("The filter must not send the screen to the beginning", position.first > 0)
        }
        val calls = collectionRepository.queries.size to transactionRepository.queries.size
        compose.onNodeWithText("Actividad").performClick()
        compose.onNodeWithText("Cobros").performClick()
        compose.waitForIdle()
        assertEquals(calls, collectionRepository.queries.size to transactionRepository.queries.size)
    }
}
