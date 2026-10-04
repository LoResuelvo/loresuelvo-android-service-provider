package com.loresuelvo.serviceprovider.acceptance.statistics

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.*
import com.loresuelvo.serviceprovider.acceptance.profile.ProviderProfileNavigationTestEntryPoint
import com.loresuelvo.serviceprovider.di.StatisticsModule
import com.loresuelvo.serviceprovider.domain.account.*
import com.loresuelvo.serviceprovider.domain.auth.*
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.paymentaccount.*
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase
import com.loresuelvo.serviceprovider.ui.components.bottomnav.*
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.statistics.ACTIVITY_READING_POSITION
import com.loresuelvo.serviceprovider.ui.screens.statistics.CONVERSION_READING_POSITION
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.*
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
class ProviderConversionNavigationAcceptanceTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    @Inject lateinit var conversion: NavigationConversionRepository
    @Inject lateinit var statistics: NavigationActivityRepository
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var entry: ActivityTestEntryPoint
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() {
        hilt.inject()
        entry = EntryPointAccessors.fromApplication(context, ActivityTestEntryPoint::class.java)
        entry.accounts().outcome = CurrentAccountOutcome.Success(CurrentAccount.Provider(
            1, "Carlos", "Gomez", "provider@example.test", Category(1, "Plumbing"), null))
        EntryPointAccessors.fromApplication(context, ProviderProfileNavigationTestEntryPoint::class.java)
            .paymentAccountRepository().outcome = PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.PENDING))
        entry.sessions().saveSession(AuthSession(User("auth0|conversion", "provider@example.test"), "synthetic-token"))
        conversion.failure = null; conversion.queries.clear(); statistics.queries.clear()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close() { scenario.close() }
    private fun bottom(route: String) = compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + route)
    private fun text(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun reveal(tag: String, id: Int): SemanticsNodeInteraction {
        compose.onNodeWithTag(tag).performScrollToNode(hasText(context.getString(id)))
        return text(id).performScrollTo()
    }
    private fun openActivity() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Activity.path).fetchSemanticsNodes().isNotEmpty() }
        bottom(Route.Activity.path).performClick(); compose.waitForIdle()
    }
    private fun openDetail() {
        reveal("provider_activity", R.string.conversion_title).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("provider_conversion").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithText(context.getString(R.string.conversion_loading)).fetchSemanticsNodes().isEmpty() }
        bottom(Route.Activity.path).assertIsSelected()
    }
    private fun conversionPosition() = compose.onNodeWithTag("provider_conversion").fetchSemanticsNode().config[CONVERSION_READING_POSITION]
    private fun dates(from: String, through: String) {
        compose.onNodeWithTag("provider_conversion").performScrollToNode(hasTestTag("conversion_from_day"))
        compose.onNodeWithTag("conversion_from_day").performTextReplacement(from)
        compose.onNodeWithTag("provider_conversion").performScrollToNode(hasTestTag("conversion_through_day"))
        compose.onNodeWithTag("conversion_through_day").performTextReplacement(through)
        androidx.test.espresso.Espresso.closeSoftKeyboard(); compose.waitForIdle()
        compose.onNodeWithTag("provider_conversion").performScrollToNode(hasTestTag("conversion_apply_period"))
        compose.onNodeWithTag("conversion_apply_period").performClick(); compose.waitForIdle()
    }

    @Test fun native_returns_reopen_recreation_and_rotation_keep_independent_period_and_reading() {
        openActivity(); assertTrue(conversion.queries.isEmpty())
        reveal("provider_activity", R.string.activity_period_options).performClick()
        compose.onNodeWithTag("activity_from_day").performTextReplacement("2026-08-01")
        compose.onNodeWithTag("activity_through_day").performTextReplacement("2026-08-31")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        reveal("provider_activity", R.string.activity_apply_period).performClick()
        reveal("provider_activity", R.string.activity_week).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.activity_compare_previous)).performClick()
        val activityQuery = statistics.queries.last()
        reveal("provider_activity", R.string.conversion_title)
        val activityPosition = compose.onNodeWithTag("provider_activity").fetchSemanticsNode().config[ACTIVITY_READING_POSITION]
        openDetail()
        compose.onNodeWithTag("conversion_period_options").performClick()
        dates("2026-09-01", "2026-09-30")
        val applied = conversion.queries.last()
        assertEquals(Instant.parse("2026-09-01T03:00:00Z"), applied.from!!.toInstant())
        assertEquals(Instant.parse("2026-10-01T03:00:00Z"), applied.to!!.toInstant())
        reveal("provider_conversion", R.string.conversion_expand).performClick()
        val anchor = reveal("provider_conversion", R.string.conversion_reported)
        val top = compose.onNodeWithTag("provider_conversion").fetchSemanticsNode().boundsInRoot.top
        val anchorTop = anchor.fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("provider_conversion").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {
            it(0f, anchorTop - top)
        }
        anchor.assertIsDisplayed()
        val position = conversionPosition(); assertTrue(position.first > 0)
        val viewport = compose.onNodeWithTag("provider_conversion").fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("Scrollable viewport ends above measured bottom bar", viewport.bottom <= bar.top)
        androidx.test.espresso.Espresso.pressBack(); compose.waitForIdle()
        assertEquals(activityPosition, compose.onNodeWithTag("provider_activity").fetchSemanticsNode().config[ACTIVITY_READING_POSITION])
        assertEquals(activityQuery, statistics.queries.last())
        openDetail(); assertEquals(position, conversionPosition()); assertEquals(applied, conversion.queries.last())
        scenario.recreate(); compose.waitForIdle()
        assertEquals(position, conversionPosition())
        scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) {
            var landscape = false
            scenario.onActivity { landscape = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            landscape && compose.onAllNodesWithText(context.getString(R.string.conversion_reported)).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("provider_conversion").fetchSemanticsNodes().firstOrNull()?.config?.getOrNull(CONVERSION_READING_POSITION) == position
        }
        text(R.string.conversion_reported).assertIsDisplayed()
        assertEquals("A mid-content anchor survives the changed viewport", position, conversionPosition())
        assertEquals(applied, conversion.queries.last())
        reveal("provider_conversion", R.string.conversion_collapse).assertIsDisplayed()
        reveal("provider_conversion", R.string.conversion_period_label).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.activity_period,
            com.loresuelvo.serviceprovider.ui.screens.statistics.formatActivityInstant(applied.from!!.toInstant()),
            com.loresuelvo.serviceprovider.ui.screens.statistics.formatActivityInstant(applied.to!!.toInstant()))).assertExists()
        bottom(Route.Activity.path).performClick(); compose.waitForIdle(); openDetail()
        reveal("provider_conversion", R.string.conversion_collapse).assertIsDisplayed()
        reveal("provider_conversion", R.string.conversion_back).performClick()
        reveal("provider_activity", R.string.activity_period_options).assertIsDisplayed()
        compose.onNodeWithTag("activity_from_day").assertTextContains("2026-08-01")
        assertEquals(activityQuery, statistics.queries.last())
        reveal("provider_activity", R.string.collections_section).performClick()
        compose.onNodeWithTag("provider_collections").assertExists()
        text(R.string.reputation_section).performClick(); compose.onNodeWithTag("provider_reputation").assertExists()
        listOf(Route.Home.path, Route.Messages.path, Route.Profile.path, Route.Activity.path).forEach { bottom(it).performClick(); compose.waitForIdle() }
        bottom(Route.Activity.path).assertIsSelected()
    }

    @Test fun invalid_dates_retry_and_session_exit_follow_real_navigation_without_fabricated_zeros() {
        openActivity(); openDetail(); compose.onNodeWithTag("conversion_period_options").performClick()
        val original = conversion.queries.last(); val calls = conversion.queries.size
        listOf(Triple("", "2026-09-30", R.string.conversion_date_incomplete),
            Triple("bad", "2026-09-30", R.string.activity_date_format_error),
            Triple("2026-09-30", "2026-09-01", R.string.activity_date_order_error),
            Triple("2026-09-01", "2026-10-05", R.string.activity_date_future_error),
            Triple("2025-09-01", "2026-09-30", R.string.activity_date_length_error)).forEach { (from, to, error) ->
                dates(from, to); reveal("provider_conversion", error).assertIsDisplayed()
                assertEquals(calls, conversion.queries.size); assertEquals(original, conversion.queries.last())
            }
        conversion.failure = ConversionOutcome.Failure.Network; dates("2026-09-01", "2026-09-30")
        val failed = conversion.queries.last()
        reveal("provider_conversion", R.string.conversion_error).assertIsDisplayed()
        compose.onAllNodesWithText("0 %").assertCountEquals(0)
        conversion.failure = null; reveal("provider_conversion", R.string.activity_retry).performClick()
        compose.waitForIdle(); assertEquals(failed, conversion.queries.last())
        reveal("provider_conversion", R.string.conversion_expand).assertIsDisplayed()
        entry.sessions().clearSession()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.welcome_login)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("provider_conversion").assertDoesNotExist()
        scenario.recreate(); compose.waitForIdle(); text(R.string.welcome_login).assertIsDisplayed()
    }
    @Module @InstallIn(SingletonComponent::class)
    object ConversionTestModule {
        @Provides @Singleton fun statistics() = NavigationActivityRepository()
        @Provides fun repository(fake: NavigationActivityRepository): ProviderActivityRepository = fake
        @Provides fun useCase(repository: ProviderActivityRepository) = GetProviderActivityUseCase(repository)
        @Provides fun collectionsRepository(): ProviderCollectionsRepository = NavigationCollectionsRepository()
        @Provides fun collectionsUseCase(repository: ProviderCollectionsRepository) =
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderCollectionsUseCase(repository)
        @Provides fun transactionsRepository(): CollectionTransactionsRepository = NavigationTransactionsRepository()
        @Provides fun transactionsUseCase(repository: CollectionTransactionsRepository) =
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetCollectionTransactionsUseCase(repository)
        @Provides @Singleton fun conversionRepository() = NavigationConversionRepository()
        @Provides fun conversionUseCase(repository: NavigationConversionRepository, clock: Clock) =
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderConversionUseCase(repository, clock)
        @Provides fun reputationUseCase() =
            com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderReputationUseCase(NavigationReputationRepository())
        @Provides fun clock(): Clock = Clock.fixed(Instant.parse("2026-10-04T12:00:00Z"), ZoneOffset.UTC)
    }
}
