package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class ProviderConversationScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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
    fun nonactive_chats_keep_media_actions_without_proposal_action() {
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
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).performClick()
        listOf(
            ConversationStatus.Pending,
            ConversationStatus.Rejected,
            ConversationStatus.Unsupported("other"),
        ).forEach { status ->
            composeTestRule.runOnIdle {
                state.value = base.copy(detail = base.detail.copy(status = status))
            }
            composeTestRule.onNodeWithTag(PROVIDER_MEDIA_ATTACH_GALLERY_ROW_TAG).assertIsDisplayed()
            composeTestRule.onNodeWithTag(PROVIDER_MEDIA_ATTACH_CAMERA_ROW_TAG).assertIsDisplayed()
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
