package com.loresuelvo.serviceprovider.acceptance.proposals

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeUiState
import com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNavHost
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListUiState
import com.loresuelvo.serviceprovider.ui.screens.home.ProviderHomeScreen
import com.loresuelvo.serviceprovider.ui.screens.proposals.ServiceProposalListScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ServiceProposalNavigationAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun detail_opens_its_conversation_from_history() {
        compose.setContent {
            val nav = rememberNavController()
            LoResuelvoNavHost(
                navController = nav,
                startDestination = Route.ServiceProposals.path,
                welcome = {}, professionalProfile = {}, optionalIdentityVerification = {}, home = {},
                serviceProposals = {
                    ServiceProposalListScreen(
                        state = ServiceProposalListUiState(proposals = listOf(proposal(12)), loading = false),
                        onSelectTab = {}, onRetry = {}, onBack = {},
                        onConversation = { nav.navigate(Route.Conversation.buildPath(it)) },
                    )
                },
                messages = {}, profile = {}, jobRequestDetail = {},
                conversation = { Text("Conversation 93") },
            )
        }
        val activity = compose.activity
        compose.onNodeWithText(activity.getString(R.string.proposal_detail_open)).performClick()
        compose.onNodeWithText(activity.getString(R.string.proposal_detail_conversation))
            .performScrollTo().performClick()
        compose.onNodeWithText("Conversation 93").assertIsDisplayed()
    }

    @Test fun back_from_detail_keeps_accepted_tab_and_history_position() {
        compose.setContent {
            var selectedTab by androidx.compose.runtime.remember {
                mutableStateOf(com.loresuelvo.serviceprovider.ui.proposals.ProposalTab.Pending)
            }
            ServiceProposalListScreen(
                state = ServiceProposalListUiState(
                    selectedTab = selectedTab,
                    proposals = (1..60).map { proposal(it).copy(status = ServiceProposalStatus.Accepted) },
                    loading = false,
                ),
                onSelectTab = { selectedTab = it }, onRetry = {}, onBack = {},
            )
        }
        val activity = compose.activity
        val accepted = activity.getString(R.string.proposal_list_accepted)
        val item = activity.getString(R.string.proposal_list_item, 42)
        compose.onNodeWithText(accepted).performClick()
        compose.onNodeWithTag("proposal_list_items").performScrollToNode(hasText(item))
        compose.onNodeWithTag("proposal_detail_open_42").performScrollTo()
        val before = compose.onNodeWithText(item).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("proposal_detail_open_42").performClick()
        compose.onNodeWithTag("proposal_detail_reason").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNode(
            hasText(accepted) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).assertIsSelected()
        compose.onNodeWithText(item).assertIsDisplayed()
        val after = compose.onNodeWithText(item).fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertEquals(before, after, 1f)
    }

    @Test fun opens_pending_proposals_from_home_and_returns_to_home() {
        compose.setContent {
            val nav = rememberNavController()
            LoResuelvoNavHost(
                navController = nav,
                startDestination = Route.Home.path,
                welcome = {}, professionalProfile = {}, optionalIdentityVerification = {},
                home = {
                    ProviderHomeScreen(
                        provider = CurrentAccount.Provider(
                            1, "Carlos", "Gómez", "provider@example.com", Category(1, "Plomería"), null,
                        ),
                        uiState = ProviderHomeUiState(
                            jobRequests = ActivitySectionState.Ready(emptyList()),
                            scheduledWork = ActivitySectionState.Ready(emptyList()),
                        ),
                        onRetryJobRequests = {}, onRetryScheduledWork = {},
                        onJobRequestClick = {}, onMercadoPagoClick = {},
                        onAllProposalsClick = { nav.navigate(Route.ServiceProposals.path) },
                    )
                },
                serviceProposals = {
                    ServiceProposalListScreen(
                        state = ServiceProposalListUiState(
                            proposals = listOf(12, 11, 10).map(::proposal),
                            loading = false,
                        ),
                        onSelectTab = {}, onRetry = {}, onBack = { nav.popBackStack() },
                    )
                },
                messages = {}, profile = {}, jobRequestDetail = {}, conversation = {},
            )
        }
        val activity = compose.activity
        compose.onNodeWithText(activity.getString(R.string.proposal_home_view_all)).performScrollTo().performClick()
        compose.onNode(
            hasText(activity.getString(R.string.proposal_list_pending)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).assertIsSelected()
        listOf(12, 11, 10).forEach {
            compose.onNodeWithTag("proposal_list_items").performScrollToNode(
                hasText(activity.getString(R.string.proposal_list_item, it)),
            )
            compose.onNodeWithText(activity.getString(R.string.proposal_list_item, it)).assertIsDisplayed()
        }
        compose.onNodeWithText(activity.getString(R.string.proposal_list_back)).performClick()
        compose.onNodeWithText(activity.getString(R.string.proposal_home_view_all)).assertIsDisplayed()
    }

    @Test fun proposal_avatar_shows_photo_and_restores_initials_after_load_error() {
        val photo = File.createTempFile("ana-avatar", ".png", compose.activity.cacheDir)
        try {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.RED)
                photo.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
                recycle()
            }
            val url = mutableStateOf<String?>(null)
            compose.setContent {
                ServiceProposalListScreen(
                    ServiceProposalListUiState(
                        proposals = listOf(proposal(12).copy(counterpart = proposal(12).counterpart.copy(
                            profilePhotoUrl = url.value,
                        ))), loading = false,
                    ),
                    onSelectTab = {}, onRetry = {}, onBack = {},
                )
            }
            compose.onNodeWithText("AP").assertIsDisplayed()
            compose.runOnIdle { url.value = photo.toURI().toString() }
            compose.waitUntil(5_000) { compose.onAllNodes(hasText("AP")).fetchSemanticsNodes().isEmpty() }
            compose.runOnIdle { url.value = File(photo.parentFile, "missing-ana-avatar.png").toURI().toString() }
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasTestTag("provider_avatar_photo_error")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("AP").assertIsDisplayed()
        } finally {
            photo.delete()
        }
    }

    private fun proposal(id: Int) = ServiceProposalSummary(
        id, 93, 1500050, Instant.parse("2026-10-06T12:00:00Z").toEpochMilli(), "Inspect sink", 45,
        ServiceProposalStatus.Pending, Instant.parse("2026-09-21T12:00:00Z").toEpochMilli(),
        ServiceProposalCounterpart(7, "consumer", "Ana", "Pérez", null, null),
        ServiceProposalBookingTerms(
            "ARS", 1500050, 1000, 1499050, 500, 100, 400, 1100, 1499450, 1500550,
            Instant.parse("2026-09-30T12:00:00Z").toEpochMilli(),
        ),
    )
}
