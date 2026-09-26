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
import androidx.compose.ui.test.performTextInput
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
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import com.loresuelvo.serviceprovider.domain.usecase.proposal.GetServiceProposalsUseCase
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListViewModel
import com.loresuelvo.serviceprovider.ui.screens.proposals.ServiceProposalListRoute
import androidx.lifecycle.Lifecycle
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.ui.screens.conversation.ChatListItem
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationScreen
import com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_INPUT_FIELD_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_SEND_BUTTON_TAG
import androidx.compose.ui.test.assertIsEnabled
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

    @Test fun returning_to_history_refreshes_the_pending_tab() {
        var status = ServiceProposalStatus.Pending
        var calls = 0
        val repository = object : ServiceProposalRepository {
            override suspend fun list(): ServiceProposalListOutcome {
                calls++
                return ServiceProposalListOutcome.Success(listOf(proposal(12).copy(status = status)))
            }
            override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome =
                error("Creation is outside this test")
        }
        val viewModel = ServiceProposalListViewModel(GetServiceProposalsUseCase(repository))
        compose.setContent {
            ServiceProposalListRoute(onBack = {}, onConversation = {}, viewModel = viewModel)
        }
        val item = compose.activity.getString(R.string.proposal_list_item, 12)
        compose.onNodeWithText(item).assertIsDisplayed()
        org.junit.Assert.assertEquals(1, calls)
        status = ServiceProposalStatus.Accepted
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText(item).assertDoesNotExist()
        org.junit.Assert.assertEquals(2, calls)
        compose.onNodeWithText(compose.activity.getString(R.string.proposal_list_pending)).assertIsSelected()
    }

    @Test fun chat_without_proposals_keeps_messages_and_composer() {
        val message = ConversationMessage(1, ConversationSender.Consumer, "Hola", 1L)
        var state by mutableStateOf(ProviderConversationUiState.Ready(
            detail = ConversationDetail(
                93, ConversationStatus.Active, ConversationCounterpart(7, "Ana", "Pérez", null),
                listOf(message), 1L,
            ),
            items = listOf(ChatListItem.ServerConfirmed(message)),
            promptInput = "",
            sending = false,
        ))
        compose.setContent {
            ProviderConversationScreen(
                state = state,
                serviceProposal = ServiceProposalListUiState(proposals = emptyList(), loading = false)
                    .proposalInConversation(93),
                onPromptChange = { state = state.copy(promptInput = it) },
                onSendClick = {}, onRetrySendFailedBubble = {}, onRetryLoad = {},
                onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
            )
        }
        compose.onNodeWithText("Hola").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.proposal_detail_chat_summary))
            .assertDoesNotExist()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).performTextInput("Llegaré a las 9")
        compose.onNodeWithTag(PROVIDER_CHAT_SEND_BUTTON_TAG).assertIsEnabled()
    }

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
                conversation = { conversationId -> Text("Conversation $conversationId") },
            )
        }
        val activity = compose.activity
        compose.onNodeWithText(activity.getString(R.string.proposal_detail_open)).performClick()
        compose.onNodeWithText(activity.getString(R.string.proposal_detail_conversation))
            .performScrollTo().performClick()
        compose.onNodeWithText("Conversation 93").assertIsDisplayed()
        compose.onNodeWithText("Conversation 12").assertDoesNotExist()
        compose.onNodeWithText("Conversation 7").assertDoesNotExist()
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
