package com.loresuelvo.serviceprovider.ui.screens.conversation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.MediaReference
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
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
                    onPlayAudio = { _, _ -> },
                    onPauseAudio = {},
                    playingMediaKey = null,
                    playingPositionMillis = 0L,
                    isPlaying = false,
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
                    onPlayAudio = { _, _ -> },
                    onPauseAudio = {},
                    playingMediaKey = null,
                    playingPositionMillis = 0L,
                    isPlaying = false,
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + 8)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Listo").assertIsDisplayed()
    }

    @Test
    fun renders_server_confirmed_audio_bubble_with_duration_and_play_control() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                MessageBubble(
                    item = ChatListItem.ServerConfirmed(
                        message = audioMessage(
                            id = 9,
                            sender = ConversationSender.Consumer,
                            url = "https://example.test/audio.webm",
                            durationMillis = 5_000L,
                        ),
                    ),
                    onRetrySendFailedBubble = {},
                    onPlayAudio = { _, _ -> },
                    onPauseAudio = {},
                    playingMediaKey = null,
                    playingPositionMillis = 0L,
                    isPlaying = false,
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + 9)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_AUDIO_TAG_PREFIX + 9)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_AUDIO_PLAY_TAG_PREFIX + 9)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_AUDIO_DURATION_TAG_PREFIX + 9)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("0:05").assertIsDisplayed()
    }

    @Test
    fun audio_play_control_invokes_onPlayAudio_when_idle() {
        val captured = mutableListOf<Pair<String, String>>()
        composeTestRule.setContent {
            LoresuelvoTheme {
                MessageBubble(
                    item = ChatListItem.ServerConfirmed(
                        message = audioMessage(
                            id = 10,
                            sender = ConversationSender.Consumer,
                            url = "https://example.test/clip.webm",
                            durationMillis = 3_000L,
                        ),
                    ),
                    onRetrySendFailedBubble = {},
                    onPlayAudio = { key, url -> captured += key to url },
                    onPauseAudio = { error("pause should not fire while idle") },
                    playingMediaKey = null,
                    playingPositionMillis = 0L,
                    isPlaying = false,
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_AUDIO_PLAY_TAG_PREFIX + 10)
            .performClick()
        assertEquals(listOf("10" to "https://example.test/clip.webm"), captured)
    }

    @Test
    fun audio_play_control_invokes_onPauseAudio_when_currently_playing() {
        var pauseInvocations = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                MessageBubble(
                    item = ChatListItem.ServerConfirmed(
                        message = audioMessage(
                            id = 11,
                            sender = ConversationSender.Consumer,
                            url = "https://example.test/clip.webm",
                            durationMillis = 3_000L,
                        ),
                    ),
                    onRetrySendFailedBubble = {},
                    onPlayAudio = { _, _ -> error("play should not fire while playing") },
                    onPauseAudio = { pauseInvocations += 1 },
                    playingMediaKey = "11",
                    playingPositionMillis = 1_500L,
                    isPlaying = true,
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_AUDIO_PLAY_TAG_PREFIX + 11)
            .performClick()
        assertEquals(1, pauseInvocations)
        composeTestRule
            .onNodeWithContentDescription("Pausar audio")
            .assertIsDisplayed()
    }

    @Test
    fun audio_play_control_invokes_onPlayAudio_when_paused() {
        val captured = mutableListOf<Pair<String, String>>()
        composeTestRule.setContent {
            LoresuelvoTheme {
                MessageBubble(
                    item = ChatListItem.ServerConfirmed(
                        message = audioMessage(
                            id = 12,
                            sender = ConversationSender.Consumer,
                            url = "https://example.test/clip.webm",
                            durationMillis = 3_000L,
                        ),
                    ),
                    onRetrySendFailedBubble = {},
                    onPlayAudio = { key, url -> captured += key to url },
                    onPauseAudio = {},
                    // Same bubble is selected (so the resume
                    // doesn't restart the audio from zero), but
                    // isPlaying flipped to false after pause().
                    playingMediaKey = "12",
                    playingPositionMillis = 1_500L,
                    isPlaying = false,
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_AUDIO_PLAY_TAG_PREFIX + 12)
            .performClick()
        assertEquals(listOf("12" to "https://example.test/clip.webm"), captured)
    }

    @Test
    fun renders_local_pending_audio_bubble_with_duration() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                MessageBubble(
                    item = ChatListItem.LocalPending(
                        key = "local-pending-audio-1",
                        sender = ConversationSender.Provider,
                        content = "",
                        createdOnEpochMillis = 1L,
                        pendingMedia = MediaUpload.Audio(
                            bytes = byteArrayOf(1, 2, 3),
                            mimeType = "audio/webm",
                            originalName = "clip.webm",
                            durationMillis = 4_000L,
                        ),
                    ),
                    onRetrySendFailedBubble = {},
                    onPlayAudio = { _, _ -> },
                    onPauseAudio = {},
                    playingMediaKey = null,
                    playingPositionMillis = 0L,
                    isPlaying = false,
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + "local-pending-audio-1")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_AUDIO_TAG_PREFIX + "local-pending-audio-1-pending")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("0:04").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGE_PENDING_INDICATOR_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun renders_pending_text_bubble_with_the_pending_indicator() {
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
                    onPlayAudio = { _, _ -> },
                    onPauseAudio = {},
                    playingMediaKey = null,
                    playingPositionMillis = 0L,
                    isPlaying = false,
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
                    onPlayAudio = { _, _ -> },
                    onPauseAudio = {},
                    playingMediaKey = null,
                    playingPositionMillis = 0L,
                    isPlaying = false,
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

    private fun audioMessage(
        id: Int,
        sender: ConversationSender,
        url: String,
        durationMillis: Long,
    ) = ConversationMessage(
        id = id,
        sender = sender,
        content = "",
        createdOnEpochMillis = 1L,
        media = MediaReference.Audio(
            id = "file-$id",
            url = url,
            mimeType = "audio/webm",
            originalName = "clip.webm",
            durationMillis = durationMillis,
        ),
    )
}
