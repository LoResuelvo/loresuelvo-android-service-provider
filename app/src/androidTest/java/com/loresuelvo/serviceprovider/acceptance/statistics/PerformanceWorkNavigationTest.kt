package com.loresuelvo.serviceprovider.acceptance.statistics

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNavHost
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.statistics.*
import com.loresuelvo.serviceprovider.ui.statistics.*
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PerformanceWorkNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun collection_opens_its_job_and_back_restores_the_movement() = verify(false, 1)
    @Test fun review_opens_its_job_and_back_restores_the_review() = verify(true, 300)

    private fun verify(review: Boolean, expectedId: Int) {
        val query = ActivityQuery(Instant.parse("2026-09-01T00:00:00Z"), Instant.parse("2026-10-01T00:00:00Z"))
        val collections = runBlocking { (NavigationCollectionsRepository().getCollections(query) as CollectionsOutcome.Success).collections }
        val transactions = runBlocking { (NavigationTransactionsRepository().getTransactions(CollectionTransactionsQuery(query.from, query.to)) as CollectionTransactionsOutcome.Success).page }
        val reputation = runBlocking { (NavigationReputationRepository().getReputation() as ReputationOutcome.Success).reputation }
        var opened: Int? = null
        compose.setContent { LoresuelvoTheme {
            val nav = rememberNavController()
            LoResuelvoNavHost(nav, Route.Activity.path,
                welcome = {}, professionalProfile = {}, optionalIdentityVerification = {}, home = {},
                messages = {}, profile = {}, jobRequestDetail = {}, conversation = {},
                activity = {
                    val onWork: (Int) -> Unit = { nav.navigate(Route.ProviderTurnDetail.buildPath(it)) }
                    if (review) ProviderReputationScreen(ProviderReputationUiState.Ready(reputation), {}, onWorkOrder = onWork)
                    else ProviderCollectionsScreen(ProviderCollectionsUiState.Ready(collections), {},
                        transactions = CollectionTransactionsUiState(page = transactions), onWorkOrder = onWork)
                },
                providerTurnDetail = { id ->
                    opened = id
                    Text(id.toString(), Modifier.testTag("opened_work"))
                })
        } }
        val listTag = if (review) "provider_reputation" else "provider_collections"
        val rowTag = if (review) "reputation_review_$expectedId" else "collection_transaction_$expectedId"
        compose.onNodeWithTag(listTag).performScrollToNode(hasTestTag(rowTag))
        compose.onNode(hasText(compose.activity.getString(R.string.performance_view_work)) and hasAnyAncestor(hasTestTag(rowTag)))
            .performScrollTo().performClick()
        compose.onNodeWithTag("opened_work").assertTextEquals(expectedId.toString())
        assertEquals(expectedId, opened)
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag(rowTag).assertIsDisplayed()
    }
}
