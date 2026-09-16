package com.loresuelvo.serviceprovider.ui.screens.conversation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.ui.screens.conversation.ChatListItem
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class MessageBubbleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renders_server_confirmed_consumer_bubble_with_the_message_text() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                MessageBubble(
                    item = ChatListItem.ServerConfirmed(
                        message = message(
                            id = 7,
                            sender = ConversationSender.Consumer,
                            content = "Hola",
                        ),
                    ),
                    onRetrySendFailedBubble = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + 7)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Hola").assertIsDisplayed()
    }

    @Test
    fun renders_server_confirmed_provider_bubble_with_the_message_text() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                MessageBubble(
                    item = ChatListItem.ServerConfirmed(
                        message = message(
                            id = 8,
                            sender = ConversationSender.Provider,
                            content = "Listo",
                        ),
                    ),
                    onRetrySendFailedBubble = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + 8)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Listo").assertIsDisplayed()
    }

    @Test
    fun renders_pending_bubble_with_the_pending_indicator() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                MessageBubble(
                    item = ChatListItem.LocalPending(
                        key = "local-pending-1",
                        sender = ConversationSender.Provider,
                        content = "Mañana",
                        createdOnEpochMillis = 1L,
                    ),
                    onRetrySendFailedBubble = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + "local-pending-1")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_PENDING_INDICATOR_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Mañana").assertIsDisplayed()
    }

    @Test
    fun renders_failed_bubble_with_a_retry_icon_and_invokes_the_callback() {
        val capturedKeys = mutableListOf<String>()
        composeTestRule.setContent {
            LoresuelvoTheme {
                MessageBubble(
                    item = ChatListItem.LocalFailed(
                        key = "local-failed-1",
                        sender = ConversationSender.Provider,
                        content = "Mañana",
                        createdOnEpochMillis = 1L,
                        pendingPrompt = "Mañana",
                    ),
                    onRetrySendFailedBubble = { capturedKeys += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + "local-failed-1")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_RETRY_BUTTON_TAG_PREFIX + "local-failed-1")
            .performClick()
        assertEquals(listOf("local-failed-1"), capturedKeys)
    }

    private fun message(id: Int, sender: ConversationSender, content: String) = ConversationMessage(
        id = id,
        sender = sender,
        content = content,
        createdOnEpochMillis = 1L,
    )
}
