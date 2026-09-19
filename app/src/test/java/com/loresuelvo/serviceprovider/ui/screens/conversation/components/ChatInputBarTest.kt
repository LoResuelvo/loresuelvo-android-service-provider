package com.loresuelvo.serviceprovider.ui.screens.conversation.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class ChatInputBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renders_the_input_field_with_the_current_prompt() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ChatInputBar(
                    promptInput = "Listo para empezar",
                    canSend = true,
                    onPromptChange = {},
                    isRecording = false,
                    onMicClick = {},
                    pendingMedia = null,
                    onAttachClick = {},
                    onClearStagedMedia = {},
                    onSendClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun send_button_is_disabled_when_canSend_is_false() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ChatInputBar(
                    promptInput = "hola",
                    canSend = false,
                    onPromptChange = {},
                    isRecording = false,
                    onMicClick = {},
                    pendingMedia = null,
                    onAttachClick = {},
                    onClearStagedMedia = {},
                    onSendClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_SEND_BUTTON_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun trailing_slot_shows_mic_button_when_input_is_blank_and_not_recording() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ChatInputBar(
                    promptInput = "",
                    canSend = false,
                    onPromptChange = {},
                    isRecording = false,
                    onMicClick = {},
                    pendingMedia = null,
                    onAttachClick = {},
                    onClearStagedMedia = {},
                    onSendClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_MIC_BUTTON_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun trailing_slot_shows_stop_button_when_recording() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ChatInputBar(
                    promptInput = "hola",
                    canSend = false,
                    onPromptChange = {},
                    isRecording = true,
                    recordingElapsedMillis = 3_000L,
                    onMicClick = {},
                    pendingMedia = null,
                    onAttachClick = {},
                    onClearStagedMedia = {},
                    onSendClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_STOP_BUTTON_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun send_button_is_enabled_when_canSend_is_true() {
        composeTestRule.setContent {
            LoresuelvoTheme {
                ChatInputBar(
                    promptInput = "hola",
                    canSend = true,
                    onPromptChange = {},
                    isRecording = false,
                    onMicClick = {},
                    pendingMedia = null,
                    onAttachClick = {},
                    onClearStagedMedia = {},
                    onSendClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_SEND_BUTTON_TAG)
            .assertIsEnabled()
    }

    @Test
    fun send_button_invokes_the_callback_when_clicked() {
        var calls = 0
        composeTestRule.setContent {
            LoresuelvoTheme {
                ChatInputBar(
                    promptInput = "hola",
                    canSend = true,
                    onPromptChange = {},
                    isRecording = false,
                    onMicClick = {},
                    pendingMedia = null,
                    onAttachClick = {},
                    onClearStagedMedia = {},
                    onSendClick = { calls += 1 },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_SEND_BUTTON_TAG)
            .performClick()

        org.junit.Assert.assertEquals(1, calls)
    }
}
