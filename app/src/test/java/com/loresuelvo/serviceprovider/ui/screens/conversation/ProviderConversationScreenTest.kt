package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_ATTACH_BUTTON_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CREATE_PROPOSAL_ROW_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_MEDIA_ATTACH_CAMERA_ROW_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_MEDIA_ATTACH_GALLERY_ROW_TAG
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class ProviderConversationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun restricted_composer_disables_input_attach_mic_send_and_retry() {
        val state = mutableStateOf(readyState("").let { it.copy(
            detail = it.detail.copy(status = ConversationStatus.Pending),
            items = listOf(ChatListItem.LocalFailed("local-failed", ConversationSender.Provider, "failed", 1L, "failed")),
        ) })
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = state.value,
                    onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                    onRetryLoad = {}, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        val prefix = "provider-message-retry-"
        for (status in listOf(ConversationStatus.Pending, ConversationStatus.Rejected, ConversationStatus.Unsupported("future"))) {
            composeTestRule.runOnIdle { state.value = state.value.copy(detail = state.value.detail.copy(status = status)) }
            composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).assertIsNotEnabled()
            composeTestRule.onNodeWithTag(com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_INPUT_FIELD_TAG).assertIsNotEnabled()
            composeTestRule.onNodeWithTag(com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_MIC_BUTTON_TAG).assertIsNotEnabled()
            composeTestRule.onNodeWithTag(prefix + "local-failed").assertIsNotEnabled()
        }
        composeTestRule.runOnIdle { state.value = state.value.copy(promptInput = "blocked") }
        composeTestRule.onNodeWithTag(com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_SEND_BUTTON_TAG).assertIsNotEnabled()
        composeTestRule.runOnIdle {
            state.value = state.value.copy(detail = state.value.detail.copy(status = ConversationStatus.Active))
        }
        composeTestRule.onNodeWithTag(com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_SEND_BUTTON_TAG).assertIsEnabled()
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).assertIsEnabled()
        composeTestRule.onNodeWithTag(prefix + "local-failed").assertIsEnabled()
    }

    @Test
    fun forbidden_error_uses_localized_copy_and_hides_private_diagnostics_and_composer() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = ProviderConversationUiState.Error(ConversationDetailOutcome.Failure.Server(403, "private diagnostic")),
                    onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                    onRetryLoad = {}, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_conversation_error_not_found)).assertIsDisplayed()
        composeTestRule.onNodeWithText("private diagnostic").assertDoesNotExist()
        composeTestRule.onNodeWithTag(PROVIDER_CONVERSATION_RETRY_LOAD_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).assertDoesNotExist()
    }

    @Test
    fun media_server_error_uses_resource_copy_instead_of_raw_diagnostics() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState("").copy(transientMediaError =
                        com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.Server(500, "private diagnostic")),
                    onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                    onRetryLoad = {}, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_conversation_media_error_server)).assertIsDisplayed()
        composeTestRule.onNodeWithText("private diagnostic").assertDoesNotExist()
    }

    @Test fun top_bar_opens_only_verified_order_id_without_proposal_sheet() {
        var openedId: Int? = null
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(""),
                    orderLinkState = ConversationOrderLinkUiState.Linked(42),
                    onOrderDetail = { openedId = it },
                    onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                    onRetryLoad = {}, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("provider_chat_order_detail").assertIsDisplayed().performClick()
        assertEquals(42, openedId)
        composeTestRule.onNodeWithTag("proposal_detail_reason").assertDoesNotExist()
    }

    @Test fun failed_order_lookup_keeps_messages_and_offers_retry() {
        var retries = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(""),
                    orderLinkState = ConversationOrderLinkUiState.Error,
                    onRetryOrderLink = { retries++ },
                    onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                    onRetryLoad = {}, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("provider_chat_order_detail").assertDoesNotExist()
        composeTestRule.onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test fun proposal_failure_keeps_chat_and_offers_retry() {
        var retries = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(""),
                    serviceProposalFailure = ServiceProposalListOutcome.Failure.Unavailable,
                    onRetryProposals = { retries++ },
                    onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                    onRetryLoad = {}, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.onNodeWithText(context.getString(R.string.proposal_list_error)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_home_retry)).assertIsEnabled().performClick()
        assertEquals(1, retries)
        composeTestRule.onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.proposal_list_empty)).assertDoesNotExist()
    }

    @Test fun pending_proposals_show_progress_without_hiding_conversation() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(""), serviceProposalLoading = true,
                    onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                    onRetryLoad = {}, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithTag("proposal_chat_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG).assertIsDisplayed()
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.onNodeWithText(context.getString(R.string.proposal_list_empty)).assertDoesNotExist()
    }

    @Test fun chat_summary_opens_the_shared_read_only_detail() {
        val proposal = ServiceProposalSummary(
            12, 42, 1500050, 1791150600000L,
            "Reparar la canilla de la cocina y revisar todas las conexiones bajo la mesada",
            90, ServiceProposalStatus.Pending, 0L,
            ServiceProposalCounterpart(7, "consumer", "Ana", "Pérez", null, null),
            ServiceProposalBookingTerms("ARS", 1500050, 0, 0, 0, 0, 0, 0, 0, 0, 0),
        )
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(""), serviceProposal = proposal,
                    onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                    onRetryLoad = {}, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Propuesta de servicio").performClick()
        composeTestRule.onNodeWithTag("proposal_detail_reason").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 hora 30 minutos").assertExists()
        composeTestRule.onNodeWithText("Ver conversación").assertExists()
    }

    @Test fun chat_summary_shows_latest_proposal_terms_before_opening_detail() {
        val previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
        try {
            val proposal = ServiceProposalSummary(
                22, 93, 2200, Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(), "Visit reason for proposal 22",
                45, ServiceProposalStatus.Pending, 0L,
                ServiceProposalCounterpart(7, "consumer", "Ana", "Pérez", null, null),
                ServiceProposalBookingTerms("ARS", 2200, 0, 0, 0, 0, 0, 0, 0, 0, 0),
            )
            composeTestRule.setContent {
                LoresuelvoTheme {
                    ProviderConversationScreen(
                        state = readyState(""), serviceProposal = proposal,
                        onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                        onRetryLoad = {}, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                    )
                }
            }
            composeTestRule.onNodeWithText("ARS 22,00").assertIsDisplayed()
            composeTestRule.onNodeWithText("Visit reason for proposal 22").assertIsDisplayed()
            composeTestRule.onNodeWithText("Pendiente").assertIsDisplayed()
            composeTestRule.onNodeWithText("el 4 de octubre de 2026 a las 21:30").assertIsDisplayed()
            composeTestRule.onNodeWithText("Propuesta de servicio").performClick()
            composeTestRule.onNodeWithText("Propuesta #22").assertExists()
        } finally {
            TimeZone.setDefault(previousZone)
        }
    }

    @Test
    fun active_chat_offers_proposal_after_media_actions() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(""), onPromptChange = {}, onSendClick = {},
                    onRetrySendFailedBubble = {}, onRetryLoad = {}, onMediaPicked = {},
                    onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).assertIsDisplayed()
    }

    @Test
    fun nonactive_chats_disable_attachment_sheet_and_proposal_action() {
        val base = readyState("")
        val state = mutableStateOf<ProviderConversationUiState>(
            base.copy(detail = base.detail.copy(status = ConversationStatus.Pending)),
        )
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = state.value, onPromptChange = {}, onSendClick = {},
                    onRetrySendFailedBubble = {}, onRetryLoad = {}, onMediaPicked = {},
                    onClearStagedMedia = {}, onClose = {},
                )
            }
        }
        listOf(
            ConversationStatus.Pending,
            ConversationStatus.Rejected,
            ConversationStatus.Unsupported("other"),
        ).forEach { status ->
            composeTestRule.runOnIdle {
                state.value = base.copy(detail = base.detail.copy(status = status))
            }
            composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).assertIsNotEnabled()
            composeTestRule.onNodeWithTag(PROVIDER_MEDIA_ATTACH_GALLERY_ROW_TAG).assertDoesNotExist()
            composeTestRule.onNodeWithTag(PROVIDER_MEDIA_ATTACH_CAMERA_ROW_TAG).assertDoesNotExist()
            composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).assertDoesNotExist()
        }
    }


    @Test
    fun renders_the_loading_indicator_when_state_is_Loading() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = ProviderConversationUiState.Loading,
                    onPromptChange = {},
                    onSendClick = {},
                    onRetrySendFailedBubble = {},
                    onRetryLoad = {},
                    onMediaPicked = {},
                    onClearStagedMedia = {},
                    onClose = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_LOADING_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).assertDoesNotExist()
    }

    @Test
    fun renders_network_error_with_a_retry_button() {
        var retryCalls = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = ProviderConversationUiState.Error(
                        failure = ConversationDetailOutcome.Failure.Network(
                            cause = RuntimeException("offline"),
                        ),
                    ),
                    onPromptChange = {},
                    onSendClick = {},
                    onRetrySendFailedBubble = {},
                    onRetryLoad = { retryCalls += 1 },
                    onMediaPicked = {},
                    onClearStagedMedia = {},
                    onClose = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_ERROR_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_RETRY_LOAD_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_RETRY_LOAD_TAG)
            .performClick()
        assertEquals(1, retryCalls)
    }

    @Test
    fun renders_NotFound_error_without_a_retry_button() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = ProviderConversationUiState.Error(
                        failure = ConversationDetailOutcome.Failure.NotFound(
                            message = "not found",
                        ),
                    ),
                    onPromptChange = {},
                    onSendClick = {},
                    onRetrySendFailedBubble = {},
                    onRetryLoad = {},
                    onMediaPicked = {},
                    onClearStagedMedia = {},
                    onClose = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_ERROR_TAG)
            .assertIsDisplayed()
        // No retry button on NotFound — the conversation is gone.
        composeTestRule.onNodeWithTag(PROVIDER_CONVERSATION_RETRY_LOAD_TAG).assertDoesNotExist()
    }

    @Test
    fun renders_Ready_state_with_header_input_and_send_button_disabled_when_blank() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(promptInput = ""),
                    onPromptChange = {},
                    onSendClick = {},
                    onRetrySendFailedBubble = {},
                    onRetryLoad = {},
                    onMediaPicked = {},
                    onClearStagedMedia = {},
                    onClose = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Ana Pérez").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(com.loresuelvo.serviceprovider.ui.screens.conversation.components
                .PROVIDER_CHAT_INPUT_FIELD_TAG)
            .assertIsDisplayed()
        // Trailing slot is the mic button when the input is blank
        // and there's no media — mirrors the consumer's pattern
        // (the send button only appears with text or staged media).
        composeTestRule
            .onNodeWithTag(com.loresuelvo.serviceprovider.ui.screens.conversation.components
                .PROVIDER_CHAT_MIC_BUTTON_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun renders_existing_messages_in_the_LazyColumn_when_present() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(
                        promptInput = "",
                        messages = listOf(
                            ConversationMessage(
                                id = 1,
                                sender = ConversationSender.Consumer,
                                content = "Hola",
                                createdOnEpochMillis = 1L,
                            ),
                            ConversationMessage(
                                id = 2,
                                sender = ConversationSender.Provider,
                                content = "Listo",
                                createdOnEpochMillis = 2L,
                            ),
                        ),
                    ),
                    onPromptChange = {},
                    onSendClick = {},
                    onRetrySendFailedBubble = {},
                    onRetryLoad = {},
                    onMediaPicked = {},
                    onClearStagedMedia = {},
                    onClose = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(
                com.loresuelvo.serviceprovider.ui.screens.conversation.components
                    .PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + 1,
            )
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(
                com.loresuelvo.serviceprovider.ui.screens.conversation.components
                    .PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + 2,
            )
            .assertIsDisplayed()
    }

    @Test
    fun ready_state_send_button_is_enabled_when_prompt_is_not_blank() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(promptInput = "hola"),
                    onPromptChange = {},
                    onSendClick = {},
                    onRetrySendFailedBubble = {},
                    onRetryLoad = {},
                    onMediaPicked = {},
                    onClearStagedMedia = {},
                    onClose = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(com.loresuelvo.serviceprovider.ui.screens.conversation.components
                .PROVIDER_CHAT_SEND_BUTTON_TAG)
            .assertIsEnabled()
    }

    @Test
    fun ready_state_close_button_invokes_onClose() {
        var closeCalls = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state = readyState(promptInput = ""),
                    onPromptChange = {},
                    onSendClick = {},
                    onRetrySendFailedBubble = {},
                    onRetryLoad = {},
                    onMediaPicked = {},
                    onClearStagedMedia = {},
                    onClose = { closeCalls += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_BACK_TAG)
            .performClick()

        assertEquals(1, closeCalls)
    }

    private fun readyState(
        promptInput: String,
        messages: List<ConversationMessage> = emptyList(),
    ): ProviderConversationUiState.Ready = ProviderConversationUiState.Ready(
        detail = ConversationDetail(
            id = 42,
            status = ConversationStatus.Active,
            counterpart = ConversationCounterpart(
                id = 7,
                name = "Ana",
                surname = "Pérez",
                profilePhotoUrl = null,
            ),
            messages = messages,
            updatedOnEpochMillis = 1L,
        ),
        items = messages.map { ChatListItem.ServerConfirmed(it) },
        promptInput = promptInput,
        sending = false,
    )
}
