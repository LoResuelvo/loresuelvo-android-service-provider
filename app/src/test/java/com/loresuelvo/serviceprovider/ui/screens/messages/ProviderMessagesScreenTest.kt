package com.loresuelvo.serviceprovider.ui.screens.messages

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessageKind
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderMessagesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun renders_consumer_identity_and_latest_message_preview() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderMessagesScreen(
                    state = MessagesListUiState.Ready(
                        listOf(
                            Conversation(
                                id = 7,
                                status = ConversationStatus.Active,
                                counterpart = ConversationCounterpart(
                                    id = 8,
                                    name = "Ana",
                                    surname = "Pérez",
                                    profilePhotoUrl = null,
                                ),
                                lastMessage = ConversationMessage(
                                    id = 9000,
                                    sender = ConversationSender.Consumer,
                                    content = "Hola, ¿podés ayudarme?",
                                    createdOnEpochMillis = System.currentTimeMillis(),
                                ),
                                updatedOnEpochMillis = System.currentTimeMillis(),
                            ),
                        ),
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText("Ana Pérez")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Hola, ¿podés ayudarme?")
            .assertIsDisplayed()
    }

    @Test
    fun renders_pending_badge_only_for_pending_conversations() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderMessagesScreen(
                    state = MessagesListUiState.Ready(
                        listOf(
                            conversation(id = 7, name = "Ana", status = ConversationStatus.Pending),
                            conversation(id = 8, name = "Luis", status = ConversationStatus.Active),
                        ),
                    ),
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_messages_pending_badge))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.provider_messages_pending_badge))
            .assertCountEquals(1)
    }

    @Test
    fun renders_empty_state_without_an_empty_list_or_error() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderMessagesScreen(state = MessagesListUiState.Ready(emptyList()))
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_messages_empty))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(PROVIDER_MESSAGES_EMPTY_TAG).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(PROVIDER_MESSAGES_LIST_TAG).assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText(context.getString(R.string.provider_messages_error))
            .assertCountEquals(0)
    }

    @Test
    fun renders_loading_indicator_with_accessible_exclusive_state() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderMessagesScreen(state = MessagesListUiState.Loading)
            }
        }

        composeTestRule.onNodeWithTag(PROVIDER_MESSAGES_LOADING_TAG).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(
                context.getString(R.string.provider_messages_loading_description),
            )
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(PROVIDER_MESSAGES_LIST_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(PROVIDER_MESSAGES_EMPTY_TAG).assertCountEquals(0)
    }

    @Test
    fun error_state_exposes_a_retry_action() {
        var retryClicks = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ProviderMessagesScreen(
                    state = MessagesListUiState.Error(
                        ConversationsOutcome.Failure.Network(IllegalStateException("offline")),
                    ),
                    onRetryClick = { retryClicks += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithText(context.getString(R.string.provider_messages_error))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGES_RETRY_TAG)
            .performClick()
        assertEquals(1, retryClicks)
    }

    private fun conversation(
        id: Int,
        name: String,
        status: ConversationStatus,
    ) = Conversation(
        id = id,
        status = status,
        counterpart = ConversationCounterpart(
            id = id + 1,
            name = name,
            surname = "Pérez",
            profilePhotoUrl = null,
        ),
        lastMessage = null,
        updatedOnEpochMillis = System.currentTimeMillis(),
    )
}
