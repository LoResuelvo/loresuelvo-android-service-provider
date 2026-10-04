package com.loresuelvo.serviceprovider.acceptance.statistics

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.*
import com.loresuelvo.serviceprovider.di.StatisticsModule
import com.loresuelvo.serviceprovider.domain.account.*
import com.loresuelvo.serviceprovider.domain.auth.*
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.*
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.statistics.COLLECTIONS_READING_POSITION
import dagger.Module
import dagger.Provides
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.*
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(StatisticsModule::class)
@RunWith(AndroidJUnit4::class)
class ProviderCollectionsNavigationAcceptanceTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @Inject lateinit var transactions: NavigationTransactionsRepository
    @Inject lateinit var collections: NavigationCollectionsRepository
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() {
        hilt.inject()
        val entry = EntryPointAccessors.fromApplication(context, ActivityTestEntryPoint::class.java)
        entry.accounts().outcome = CurrentAccountOutcome.Success(CurrentAccount.Provider(
            1, "Carlos", "Gomez", "provider@example.test", Category(1, "Plumbing"), null))
        entry.sessions().saveSession(AuthSession(User("auth0|collections", "provider@example.test"), "synthetic-token"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close() { scenario.close() }

    @Test fun tabs_filters_continuation_and_reading_survive_navigation_recreation_and_rotation() {
        openCollections()
        revealControl(R.string.collections_evolution).assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
            context.getString(R.string.activity_collapsed))).performClick()
        revealControl(R.string.collections_evolution).assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
            context.getString(R.string.activity_expanded)))
        revealControl(R.string.collections_filter_deposits).performClick()
        revealControl(R.string.collections_load_more).performClick()
        compose.waitForIdle()
        assertEquals(CollectionPurpose.BOOKING_DEPOSIT, transactions.queries.last().purpose)
        assertEquals("next", transactions.queries.last().cursor)
        val reference = context.getString(R.string.collections_proposal_reference, 19)
        compose.onNodeWithTag("provider_collections").performScrollToNode(hasText(reference))
        compose.onNodeWithText(reference).assertIsDisplayed()
        val rowTop = compose.onNodeWithTag("collection_transaction_19").fetchSemanticsNode().boundsInRoot.top
        val tabsBottom = text(R.string.collections_section).fetchSemanticsNode().boundsInRoot.bottom
        compose.onNodeWithTag("provider_collections").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { scroll -> scroll(0f, rowTop - tabsBottom) }
        compose.onNodeWithText(reference).assertIsDisplayed()
        val before = readingPosition()
        val viewport = compose.onNodeWithTag("provider_collections").fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Activity.path).fetchSemanticsNode().boundsInRoot
        assertTrue("Collection viewport must clear the bottom navigation", viewport.bottom <= bar.top)
        text(R.string.activity_section).assertIsDisplayed().performClick()
        text(R.string.collections_section).performClick()
        compose.waitForIdle()
        assertEquals(before, readingPosition())
        val anchored = before
        assertTrue(before.first > 0)
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Activity.path).performClick()
        compose.waitForIdle()
        assertEquals(anchored, readingPosition())
        compose.onNodeWithText(reference).assertIsDisplayed()
        scenario.recreate(); compose.waitForIdle()
        assertEquals(anchored, readingPosition())
        scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) {
            context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                compose.onAllNodesWithTag("provider_collections").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        assertEquals("Rotation must preserve the first visible item and its pixel offset", anchored, readingPosition())
        compose.onNodeWithText(reference).assertIsDisplayed()
        compose.onNodeWithTag("collection_transaction_19").assertIsDisplayed()
        assertEquals(CollectionPurpose.BOOKING_DEPOSIT, transactions.queries.last().purpose)
        assertEquals(collections.queries.last().from, transactions.queries.last().from)
        revealControl(R.string.collections_evolution).assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
            context.getString(R.string.activity_expanded)))
    }

    @Test fun detail_retry_preserves_summary_and_selected_filter() {
        openCollections()
        transactions.failNext = true
        revealControl(R.string.collections_filter_deposits).performClick()
        revealControl(R.string.activity_retry).assertHasClickAction().performClick()
        compose.waitForIdle()
        assertEquals(CollectionPurpose.BOOKING_DEPOSIT, transactions.queries.last().purpose)
        assertEquals(1, collections.queries.size)
        reveal(R.string.collections_verified_total).assertIsDisplayed()
        reveal(R.string.collections_money_note).assertIsDisplayed()
    }


    @Test fun period_and_granularity_controls_reset_continuation_and_preserve_purpose() {
        openCollections()
        revealControl(R.string.collections_filter_deposits).performClick()
        revealControl(R.string.collections_load_more).performClick()
        compose.waitForIdle()
        assertEquals("next", transactions.queries.last().cursor)
        revealControl(R.string.activity_period_options).performClick()
        compose.onNodeWithTag("activity_from_day").performTextReplacement("2026-08-01")
        compose.onNodeWithTag("activity_through_day").performTextReplacement("2026-08-31")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        revealControl(R.string.activity_apply_period).performClick()
        compose.waitForIdle()
        assertEquals(Instant.parse("2026-08-01T03:00:00Z"), transactions.queries.last().from)
        assertNull(transactions.queries.last().cursor)
        assertEquals(CollectionPurpose.BOOKING_DEPOSIT, transactions.queries.last().purpose)
        listOf(R.string.activity_week to ActivityGranularity.WEEK,
            R.string.activity_month to ActivityGranularity.MONTH,
            R.string.activity_day to ActivityGranularity.DAY).forEach { (label, granularity) ->
            revealControl(label).performClick(); compose.waitForIdle()
            assertEquals(granularity, collections.queries.last().granularity)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.activity_compare_previous))
            .performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(collections.queries.last().comparePrevious)
    }

    private fun openCollections() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Activity.path).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Activity.path).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.collections_section)).fetchSemanticsNodes().isNotEmpty() }
        text(R.string.collections_section).assertHasClickAction().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.collections_verified_total)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun text(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun reveal(id: Int): SemanticsNodeInteraction {
        compose.onNodeWithTag("provider_collections").performScrollToNode(hasText(context.getString(id)))
        return text(id).performScrollTo()
    }
    private fun revealControl(id: Int): SemanticsNodeInteraction {
        val matcher = hasText(context.getString(id)) and hasClickAction()
        compose.onNodeWithTag("provider_collections").performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun readingPosition() = compose.onNodeWithTag("provider_collections")
        .fetchSemanticsNode().config[COLLECTIONS_READING_POSITION]

    @Module @InstallIn(SingletonComponent::class)
    object TestStatisticsModule {
        @Provides fun activityRepository(): ProviderActivityRepository = NavigationActivityRepository()
        @Provides fun activityUseCase(repository: ProviderActivityRepository) = GetProviderActivityUseCase(repository)
        @Provides @Singleton fun collections() = NavigationCollectionsRepository()
        @Provides fun collectionsRepository(fake: NavigationCollectionsRepository): ProviderCollectionsRepository = fake
        @Provides fun collectionsUseCase(repository: ProviderCollectionsRepository) = GetProviderCollectionsUseCase(repository)
        @Provides @Singleton fun transactions() = NavigationTransactionsRepository()
        @Provides fun transactionsRepository(fake: NavigationTransactionsRepository): CollectionTransactionsRepository = fake
        @Provides fun transactionsUseCase(repository: CollectionTransactionsRepository) = GetCollectionTransactionsUseCase(repository)
        @Provides fun reputationUseCase() =
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderReputationUseCase(
                object : com.loresuelvo.serviceprovider.domain.statistics.ProviderReputationRepository {
                    override suspend fun getReputation(cursor: String?) =
                        com.loresuelvo.serviceprovider.domain.statistics.ReputationOutcome.Failure.Network
                })
        @Provides fun clock(): Clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)
    }
}
