package com.loresuelvo.serviceprovider.acceptance.statistics

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.profile.ProviderProfileNavigationTestEntryPoint
import com.loresuelvo.serviceprovider.di.StatisticsModule
import com.loresuelvo.serviceprovider.domain.account.*
import com.loresuelvo.serviceprovider.domain.auth.*
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.*
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.statistics.REPUTATION_READING_POSITION
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
class ProviderReputationNavigationAcceptanceTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @Inject lateinit var reputation: NavigationReputationRepository
    @Inject lateinit var transactions: NavigationTransactionsRepository
    @Inject lateinit var collections: NavigationCollectionsRepository
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() {
        hilt.inject()
        val entry = entry()
        entry.accounts().outcome = CurrentAccountOutcome.Success(CurrentAccount.Provider(
            1, "Carlos", "Gomez", "provider@example.test", Category(1, "Plumbing"), null))
        EntryPointAccessors.fromApplication(context, ProviderProfileNavigationTestEntryPoint::class.java)
            .paymentAccountRepository().outcome = PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.PENDING))
        entry.sessions().saveSession(AuthSession(User("auth0|reputation", "provider@example.test"), "synthetic-token"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close() { scenario.close() }

    @Test fun deep_reading_survives_tabs_bottom_return_back_recreation_and_rotation() {
        openReputation(); loadMore(); loadMore(); anchor(250)
        val before = position(); assertTrue(before.first > 20)
        text(R.string.activity_section).assertIsDisplayed().performClick()
        text(R.string.reputation_section).performClick(); compose.waitForIdle()
        assertEquals(before, position()); row(250).assertIsDisplayed()
        bottom(Route.Messages).performClick(); bottom(Route.Activity).performClick(); compose.waitForIdle()
        assertEquals(before, position()); row(250).assertIsDisplayed()
        bottom(Route.Messages).performClick()
        androidx.test.espresso.Espresso.pressBack(); compose.waitForIdle()
        bottom(Route.Activity).performClick(); compose.waitForIdle()
        assertEquals(before, position()); row(250).assertIsDisplayed()
        scenario.recreate(); compose.waitForIdle()
        assertEquals(before, position()); row(250).assertIsDisplayed()
        scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) {
            context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                compose.onAllNodesWithTag("provider_reputation").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle(); assertEquals(before, position()); row(250).assertIsDisplayed()
        val viewport = list().fetchSemanticsNode().boundsInRoot
        assertTrue("Reputation must clear bottom navigation", viewport.bottom <= bottom(Route.Activity).fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithText(context.getString(R.string.activity_period_options)).assertDoesNotExist()
    }

    @Test fun continuation_retry_retains_rows_and_refresh_replaces_all_pages_and_resets_reading() {
        openReputation(); reputation.failNext = true
        revealControl(R.string.reputation_load_more).performClick()
        list().performScrollToNode(hasText(context.getString(R.string.reputation_work, 300)))
        row(300).assertIsDisplayed()
        revealControl(R.string.activity_retry).performClick(); compose.waitForIdle()
        assertEquals(listOf(null, "opaque+/first=", "opaque+/first="), reputation.cursors)
        anchor(275); row(275).assertIsDisplayed()
        revealControl(R.string.reputation_refresh)
        reputation.refreshed = true
        text(R.string.reputation_refresh).performClick(); compose.waitForIdle()
        assertEquals(0 to 0, position()); assertNull(reputation.cursors.last())
        list().performScrollToNode(hasText(context.getString(R.string.reputation_work, 400)))
        row(400).assertIsDisplayed(); row(275).assertDoesNotExist(); row(300).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.reputation_load_more)).assertDoesNotExist()
    }

    @Test fun initial_error_retry_exposes_no_invented_results_and_recovers_actual_first_page() {
        reputation.failNext = true
        openReputation()
        text(R.string.reputation_error).assertIsDisplayed()
        row(300).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.reputation_sample, 0L)).assertDoesNotExist()
        text(R.string.activity_retry).assertHasClickAction().performClick(); compose.waitForIdle()
        assertEquals(listOf(null, null), reputation.cursors)
        list().performScrollToNode(hasText(context.getString(R.string.reputation_work, 300)))
        row(300).assertIsDisplayed()
    }

    @Test fun activity_and_collections_options_and_reading_remain_independent_of_lifetime_reputation() {
        openPerformance(); text(R.string.collections_section).performClick()
        collectionsControl(R.string.collections_filter_deposits).performClick()
        collectionsControl(R.string.collections_load_more).performClick(); compose.waitForIdle()
        val reference = context.getString(R.string.collections_proposal_reference, 19)
        compose.onNodeWithTag("provider_collections").performScrollToNode(hasText(reference))
        val collectionsPosition = compose.onNodeWithTag("provider_collections").fetchSemanticsNode().config[
            com.loresuelvo.serviceprovider.ui.screens.statistics.COLLECTIONS_READING_POSITION]
        text(R.string.reputation_section).performClick(); compose.waitForIdle(); loadMore(); anchor(275)
        val reputationPosition = position()
        text(R.string.collections_section).performClick(); compose.waitForIdle()
        assertEquals(CollectionPurpose.BOOKING_DEPOSIT, transactions.queries.last().purpose)
        assertEquals(collectionsPosition, compose.onNodeWithTag("provider_collections").fetchSemanticsNode().config[
            com.loresuelvo.serviceprovider.ui.screens.statistics.COLLECTIONS_READING_POSITION])
        text(R.string.activity_section).performClick()
        compose.onNodeWithTag("provider_activity").performScrollToNode(hasText(context.getString(R.string.activity_period_options)))
        text(R.string.activity_period_options).performClick()
        compose.onNodeWithTag("provider_activity").performScrollToNode(hasText(context.getString(R.string.activity_week)))
        text(R.string.activity_week).performClick(); compose.waitForIdle()
        compose.onNodeWithTag("provider_activity").performScrollToNode(hasText(context.getString(R.string.activity_evolution)))
        val activityPosition = compose.onNodeWithTag("provider_activity").fetchSemanticsNode().config[
            com.loresuelvo.serviceprovider.ui.screens.statistics.ACTIVITY_READING_POSITION]
        text(R.string.reputation_section).assertIsDisplayed().performClick(); compose.waitForIdle()
        assertEquals(reputationPosition, position())
        text(R.string.collections_section).performClick(); compose.waitForIdle()
        assertEquals(ActivityGranularity.WEEK, collections.queries.last().granularity)
        assertEquals(CollectionPurpose.BOOKING_DEPOSIT, transactions.queries.last().purpose)
        text(R.string.reputation_section).performClick(); compose.waitForIdle()
        assertEquals(reputationPosition, position())
        text(R.string.activity_section).performClick(); compose.waitForIdle()
        assertEquals(activityPosition, compose.onNodeWithTag("provider_activity").fetchSemanticsNode().config[
            com.loresuelvo.serviceprovider.ui.screens.statistics.ACTIVITY_READING_POSITION])
    }

    @Test fun private_reviews_are_erased_on_logout_and_profile_has_no_sample_rating_or_extra_destination() {
        openReputation()
        list().performScrollToNode(hasText(context.getString(R.string.reputation_work, 300)))
        row(300).assertIsDisplayed()
        listOf(Route.Home, Route.Messages, Route.Activity, Route.Profile).forEach { bottom(it).assertExists() }
        compose.onAllNodes(hasTestTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Home.path) or
            hasTestTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path) or
            hasTestTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Activity.path) or
            hasTestTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Profile.path)).assertCountEquals(4)
        bottom(Route.Profile).performClick(); compose.waitForIdle()
        compose.onNodeWithTag(com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_PROFILE_DATA_TAG).assertIsDisplayed()
        bottom(Route.Profile).assertIsSelected()
        assertNotNull("Opening Profile must retain the shared authenticated session", entry().sessions().getSession())
        compose.onNodeWithText(context.getString(R.string.provider_profile_sample_rating)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.provider_profile_rating, 4.9)).assertDoesNotExist()
        bottom(Route.Activity).performClick(); compose.waitForIdle(); row(300).assertIsDisplayed()
        compose.runOnIdle { entry().sessions().clearSession() }
        compose.waitForIdle(); row(300).assertDoesNotExist()
        compose.onNodeWithTag("provider_reputation").assertDoesNotExist()
    }

    private fun entry() = EntryPointAccessors.fromApplication(context, ActivityTestEntryPoint::class.java)
    private fun text(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun bottom(route: Route) = compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + route.path)
    private fun list() = compose.onNodeWithTag("provider_reputation")
    private fun row(id: Int) = compose.onNodeWithTag("reputation_review_$id")
    private fun position() = list().fetchSemanticsNode().config[REPUTATION_READING_POSITION]
    private fun openPerformance() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Activity.path).fetchSemanticsNodes().isNotEmpty() }
        bottom(Route.Activity).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.reputation_section)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun openReputation() { openPerformance(); text(R.string.reputation_section).performClick(); compose.waitForIdle() }
    private fun revealControl(id: Int): SemanticsNodeInteraction {
        val matcher = hasText(context.getString(id)) and hasClickAction()
        list().performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun collectionsControl(id: Int): SemanticsNodeInteraction {
        val matcher = hasText(context.getString(id)) and hasClickAction()
        compose.onNodeWithTag("provider_collections").performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun loadMore() { revealControl(R.string.reputation_load_more).performClick(); compose.waitForIdle() }
    private fun anchor(id: Int) {
        list().performScrollToNode(hasText(context.getString(R.string.reputation_work, id)))
        val top = row(id).fetchSemanticsNode().boundsInRoot.top
        val tabsBottom = text(R.string.reputation_section).fetchSemanticsNode().boundsInRoot.bottom
        list().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, top - tabsBottom) }
        compose.waitForIdle(); row(id).assertIsDisplayed()
    }

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
        @Provides @Singleton fun reputation() = NavigationReputationRepository()
        @Provides fun reputationUseCase(fake: NavigationReputationRepository) = GetProviderReputationUseCase(fake)
        @Provides fun conversionUseCase(clock: Clock) =
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderConversionUseCase(
                object : com.loresuelvo.serviceprovider.domain.statistics.ProviderConversionRepository {
                    override suspend fun getConversion(query: com.loresuelvo.serviceprovider.domain.statistics.ConversionQuery) =
                        com.loresuelvo.serviceprovider.domain.statistics.ConversionOutcome.Failure.Network
                }, clock)
        @Provides fun clock(): Clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)
    }
}
