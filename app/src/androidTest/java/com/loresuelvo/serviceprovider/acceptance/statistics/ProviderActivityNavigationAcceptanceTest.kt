package com.loresuelvo.serviceprovider.acceptance.statistics

import android.content.Context
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.unit.dp
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
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.navigation.Route
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.*
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Singleton
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@HiltAndroidTest
@UninstallModules(StatisticsModule::class)
@RunWith(AndroidJUnit4::class)
class ProviderActivityNavigationAcceptanceTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var entry: ActivityTestEntryPoint
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun setup() {
        hilt.inject()
        entry = EntryPointAccessors.fromApplication(context, ActivityTestEntryPoint::class.java)
        entry.accounts().outcome = CurrentAccountOutcome.Success(CurrentAccount.Provider(
            1, "Carlos", "Gomez", "provider@example.test", Category(1, "Plumbing"), null))
        entry.sessions().saveSession(AuthSession(User("auth0|activity", "provider@example.test"), "synthetic-token"))
        entry.statistics().unauthorized = false
        entry.statistics().queries.clear()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }
    @After fun close() { scenario.close() }

    @Test fun entry_has_visible_labels_and_restores_period_options_and_reading_after_messages_and_recreation() {
        openActivity()
        text(R.string.activity_bookings).performScrollTo().assertExists()
        text(R.string.activity_completions).assertExists()
        text(R.string.activity_period_options).performScrollTo().performClick()
        compose.onNodeWithTag("activity_from_day").performTextReplacement("2026-08-01")
        compose.onNodeWithTag("activity_through_day").performTextReplacement("2026-08-31")
        text(R.string.activity_apply_period).performScrollTo().performClick()
        text(R.string.activity_week).performScrollTo().performClick()
        compose.waitForIdle()
        val query = entry.statistics().queries.last()
        assertEquals(Instant.parse("2026-08-01T03:00:00Z"), query.from)
        assertEquals(ActivityGranularity.WEEK, query.granularity)
        text(R.string.activity_evolution_values).performScrollTo().performClick()
        text(R.string.activity_pending).performScrollTo().assertIsDisplayed()
        val before = scrollPosition()
        assertTrue("Restoration must preserve a scrolled reading position", before > 0f)
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        openActivity()
        text(R.string.activity_pending).assertIsDisplayed()
        assertEquals(before, scrollPosition())
        scenario.recreate()
        compose.waitForIdle()
        text(R.string.activity_pending).assertIsDisplayed()
        assertEquals(before, scrollPosition())
        assertEquals(query, entry.statistics().queries.last())
        scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(5_000) {
            context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                compose.onAllNodesWithText(context.getString(R.string.activity_pending)).fetchSemanticsNodes().isNotEmpty()
        }
        text(R.string.activity_pending).assertIsDisplayed()
        assertEquals(query, entry.statistics().queries.last())
        // The expanded values remain in the tree after the loading branch and recreation.
        compose.onNodeWithTag("provider_activity").performScrollToNode(
            hasText(context.getString(R.string.activity_bucket_values, 8L, 5L, 3L)))
        compose.onNodeWithText(context.getString(R.string.activity_bucket_values, 8L, 5L, 3L)).assertExists()
    }

    @Test fun expired_query_returns_to_login_and_private_results_cannot_be_reopened() {
        openActivity()
        text(R.string.activity_bookings).performScrollTo().assertExists()
        entry.statistics().unauthorized = true
        text(R.string.activity_period_options).performScrollTo().performClick()
        text(R.string.activity_week).performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.welcome_login)).fetchSemanticsNodes().isNotEmpty() }
        text(R.string.welcome_login).assertIsDisplayed()
        compose.onNodeWithTag("provider_activity").assertDoesNotExist()
        assertNull(entry.sessions().getSession())
        scenario.recreate()
        text(R.string.welcome_login).assertIsDisplayed()
        compose.onNodeWithTag("provider_activity").assertDoesNotExist()
    }

    private fun openActivity() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Activity.path).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Activity.path).assertHasClickAction().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(context.getString(R.string.activity_results)).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun text(id: Int) = compose.onNodeWithText(context.getString(id))
    private fun scrollPosition(): Float = compose.onNodeWithTag("provider_activity").fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()

    @Module @InstallIn(SingletonComponent::class)
    object ActivityTestModule {
        @Provides @Singleton fun statistics() = NavigationActivityRepository()
        @Provides fun repository(fake: NavigationActivityRepository): ProviderActivityRepository = fake
        @Provides fun useCase(repository: ProviderActivityRepository) = GetProviderActivityUseCase(repository)
        @Provides fun clock(): Clock = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC)
    }
}

class NavigationActivityRepository : ProviderActivityRepository {
    var unauthorized = false
    val queries = mutableListOf<ActivityQuery>()
    override suspend fun getActivity(query: ActivityQuery): ActivityOutcome {
        queries += query
        if (unauthorized) return ActivityOutcome.Failure.Unauthorized
        return ActivityOutcome.Success(ProviderActivity(
            ActivityPeriod(query.from, query.to, query.granularity.name.lowercase(), "America/Argentina/Buenos_Aires"),
            query.to, ActivityResults(8, 5, 3, 4, 3, 1, 12345678, 2469136, "ARS"),
            listOf(ActivityBucket(query.from, query.to, 8, 5, 3)), CurrentPending(2, 7, 4)))
    }
}

@EntryPoint @InstallIn(SingletonComponent::class)
interface ActivityTestEntryPoint {
    fun sessions(): ProviderSignupSessionStore
    fun accounts(): ProviderSignupCurrentAccountRepository
    fun statistics(): NavigationActivityRepository
}

@RunWith(AndroidJUnit4::class)
class ProviderActivityCompactLayoutTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.createComposeRule()

    @Test fun compact_width_and_large_font_keep_labels_values_and_pending_work_accessible() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val query = ActivityQuery(Instant.parse("2026-09-03T12:00:00Z"), Instant.parse("2026-10-03T12:00:00Z"))
        val activity = kotlinx.coroutines.runBlocking {
            (NavigationActivityRepository().getActivity(query) as ActivityOutcome.Success).activity
        }
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                androidx.compose.material3.MaterialTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier
                        .requiredWidth(280.dp)) {
                        com.loresuelvo.serviceprovider.ui.screens.statistics.ProviderActivityScreen(
                            com.loresuelvo.serviceprovider.ui.statistics.ProviderActivityUiState.Ready(activity), {})
                    }
                }
            }
        }
        listOf(R.string.activity_bookings, R.string.activity_completions, R.string.activity_paid,
            R.string.activity_clients, R.string.activity_evolution_values, R.string.activity_awaiting_payment).forEach { id ->
            val node = compose.onNodeWithText(context.getString(id))
            node.performScrollTo().assertIsDisplayed()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("provider_activity").fetchSemanticsNode().boundsInRoot
            assertTrue("Label must fit the compact viewport", bounds.left >= viewport.left && bounds.right <= viewport.right)
        }
    }
}
