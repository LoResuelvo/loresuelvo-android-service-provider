package com.loresuelvo.serviceprovider.ui.screens.conversation

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_INPUT_FIELD_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class ConversationReadingScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(ready())

    private fun message(id: Int, text: String = "History message $id") =
        ConversationMessage(id, ConversationSender.Consumer, text, id.toLong())

    private fun ready(): ProviderConversationUiState.Ready {
        val messages = (1..30).map { message(it) }
        return ProviderConversationUiState.Ready(
            ConversationDetail(42, ConversationStatus.Active, ConversationCounterpart(7, "Ana", "Perez", null), messages, 30),
            messages.map { ChatListItem.ServerConfirmed(it) }, "Unsent reply", false,
        )
    }

    private fun show() {
        compose.setContent {
            LoresuelvoTheme {
                ProviderConversationScreen(
                    state.value, onPromptChange = { state.value = state.value.copy(promptInput = it) },
                    onSendClick = {}, onRetrySendFailedBubble = {}, onRetryLoad = {},
                    onMediaPicked = {}, onClearStagedMedia = {}, onClose = {}, modifier = Modifier,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun append(id: Int, text: String = "New message $id", own: Boolean = false) {
        compose.runOnIdle {
            val item = if (own) ChatListItem.LocalPending("own-$id", ConversationSender.Provider, text, id.toLong())
                else ChatListItem.ServerConfirmed(message(id, text))
            state.value = state.value.copy(items = state.value.items + item)
        }
        compose.waitForIdle()
    }

    private fun bubble(id: Int) = compose.onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + id)
    private fun list() = compose.onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG)
    private fun notice() = compose.onNodeWithTag(PROVIDER_CONVERSATION_NEW_MESSAGE_TAG)

    @Test fun settled_bottom_follows_arrival_without_showing_notice() {
        show()
        bubble(30).assertIsDisplayed()
        append(31)
        bubble(31).assertIsDisplayed()
        notice().assertDoesNotExist()
        assertTrue(bubble(31).fetchSemanticsNode().boundsInRoot.bottom <= list().fetchSemanticsNode().boundsInRoot.bottom)
    }

    @Test fun history_anchor_and_draft_survive_two_arrivals_and_action_goes_to_latest() {
        show()
        list().performScrollToIndex(8)
        compose.waitForIdle()
        val anchor = bubble(9).fetchSemanticsNode().boundsInRoot
        append(31)
        append(32)
        assertEquals(anchor, bubble(9).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Unsent reply")
        compose.onAllNodesWithTag(PROVIDER_CONVERSATION_NEW_MESSAGE_TAG).assertCountEquals(1)
        val context = ApplicationProvider.getApplicationContext<Context>()
        notice().assertTextEquals(context.getString(R.string.provider_conversation_new_message))
            .assertHasClickAction().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertHeightIsAtLeast(androidx.compose.ui.unit.Dp(48f))
            .performClick()
        compose.waitForIdle()
        bubble(32).assertIsDisplayed()
        notice().assertDoesNotExist()
        assertEquals(listOf("31", "32"), state.value.items.takeLast(2).map { it.key })
    }

    @Test fun partially_visible_tall_last_message_is_history_and_manual_bottom_clears_notice() {
        val tall = message(30, (1..70).joinToString("\n") { "Long message line $it" })
        state.value = state.value.copy(items = state.value.items.dropLast(1) + ChatListItem.ServerConfirmed(tall))
        show()
        list().performScrollToIndex(29)
        compose.waitForIdle()
        val anchor = bubble(30).getUnclippedBoundsInRoot()
        assertTrue(anchor.bottom > list().getUnclippedBoundsInRoot().bottom)
        append(31)
        assertEquals(anchor, bubble(30).getUnclippedBoundsInRoot())
        notice().assertIsDisplayed()
        list().performScrollToIndex(30)
        compose.waitForIdle()
        bubble(31).assertIsDisplayed()
        notice().assertDoesNotExist()
    }

    @Test fun an_arrival_during_an_active_drag_does_not_take_over_scrolling() {
        show()
        list().performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 80f))
        }
        compose.waitForIdle()
        val anchor = bubble(29).getUnclippedBoundsInRoot()
        append(31)
        assertEquals(anchor, bubble(29).getUnclippedBoundsInRoot())
        notice().assertIsDisplayed()
        list().performTouchInput { up() }
        compose.waitForIdle()
        notice().assertIsDisplayed()
    }

    @Test fun duplicate_refresh_and_playback_changes_preserve_anchor_but_own_send_follows() {
        show()
        list().performScrollToIndex(8)
        compose.waitForIdle()
        val anchor = bubble(9).fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            state.value = state.value.copy(items = state.value.items.toList(), playingMediaKey = "1", playingPositionMillis = 500)
        }
        compose.waitForIdle()
        assertEquals(anchor, bubble(9).fetchSemanticsNode().boundsInRoot)
        notice().assertDoesNotExist()
        append(31, own = true)
        compose.onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + "own-31").assertIsDisplayed()
        notice().assertDoesNotExist()
    }
}
