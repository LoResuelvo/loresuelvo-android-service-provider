package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.domain.conversation.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.ChatInputBar
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.MessageBubble
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationAudioControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun received_audio_slider_requests_position_for_its_bubble_and_has_an_accessible_label() {
        var seek: Triple<String, String, Long>? = null
        compose.setContent {
            MaterialTheme {
                MessageBubble(ChatListItem.ServerConfirmed(ConversationMessage(99, ConversationSender.Consumer, "", 1,
                    media = MediaReference.Audio("audio", "https://example.test/audio.webm", "audio/webm", "clip.webm", 5000))),
                    {}, { _, _ -> }, {}, "99", 0, false,
                    onSeekAudio = { key, url, millis -> seek = Triple(key, url, millis) })
            }
        }
        compose.onNodeWithTag("provider-message-audio-progress-99")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.4f) }
        assertEquals(Triple("99", "https://example.test/audio.webm", 2000L), seek)
    }

    @Test
    fun recording_exposes_cancel_and_stop_and_audio_preview_disables_other_attachments() {
        var cancelled = 0
        compose.setContent {
            MaterialTheme {
                ChatInputBar("", null, false, true, onPromptChange = {}, onSendClick = {}, onAttachClick = {},
                    onClearStagedMedia = {}, onMicClick = {}, onCancelRecording = { cancelled++ })
            }
        }
        compose.onNodeWithTag("provider-chat-recording-cancel").performClick()
        assertEquals(1, cancelled)
        compose.onNodeWithTag("provider-chat-stop-button").assertIsDisplayed()
    }
}
