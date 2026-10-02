package com.loresuelvo.serviceprovider.acceptance.messaging

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.ui.screens.conversation.ConversationAudioPermission
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.ChatInputBar
import com.loresuelvo.serviceprovider.domain.conversation.MediaUpload
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationAudioBoundaryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun denial_grant_repeated_taps_and_delayed_cross_conversation_result_are_bound_to_origin() {
        val registry = ControlledRegistry()
        val conversation = mutableIntStateOf(42)
        var granted = 0
        var denied = 0
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) {
                MaterialTheme {
                    ConversationAudioPermission(conversation.intValue, { true }, { granted++ }, { denied++ }) { request ->
                        Button(onClick = request) { Text("Record") }
                    }
                }
            }
        }
        compose.onNodeWithText("Record").performClick().performClick()
        assertEquals(1, registry.launches)
        assertTrue(registry.contract is ActivityResultContracts.RequestPermission)
        compose.runOnIdle { registry.dispatchResult(registry.code, false) }
        assertEquals(1, denied)
        assertEquals(0, granted)
        compose.onNodeWithText("Record").performClick()
        compose.runOnIdle { registry.dispatchResult(registry.code, true) }
        assertEquals(1, granted)
        compose.onNodeWithText("Record").performClick()
        compose.runOnIdle { conversation.intValue = 99 }
        compose.runOnIdle { registry.dispatchResult(registry.code, true) }
        assertEquals(1, granted)
    }

    @Test
    fun recording_cancel_and_local_preview_controls_forward_only_the_owned_action() {
        val recording = mutableStateOf(true)
        val audio = MediaUpload.Audio(byteArrayOf(1), "audio/webm", "clip.webm", 3000)
        var cancels = 0
        var plays = 0
        var pauses = 0
        var clears = 0
        val playing = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                ChatInputBar("", if (recording.value) null else audio, !recording.value, recording.value,
                    onPromptChange = {}, onSendClick = {}, onAttachClick = {}, onClearStagedMedia = { clears++ },
                    onMicClick = {}, onCancelRecording = { cancels++; recording.value = false },
                    onPlayPreview = { plays++; playing.value = true }, onPausePreview = { pauses++; playing.value = false },
                    previewPlaying = playing.value)
            }
        }
        compose.onNodeWithTag("provider-chat-recording-cancel").performClick()
        assertEquals(1, cancels)
        compose.onNodeWithTag("provider-chat-preview-play").performClick().performClick()
        assertEquals(1, plays)
        assertEquals(1, pauses)
        compose.onNodeWithTag("provider-chat-media-preview-clear").performClick()
        assertEquals(1, clears)
    }

    @Test
    fun activity_recreation_cancels_real_model_capture_without_second_recording_or_post() {
        var starts = 0
        var cancels = 0
        var sends = 0
        lateinit var model: com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationViewModel
        val recorder = object : com.loresuelvo.serviceprovider.domain.conversation.AudioRecorder {
            override fun start(): Result<Unit> { starts++; return Result.success(Unit) }
            override fun stop(): Result<String> = error("Recreation must cancel")
            override fun discard(uri: String) = Unit
            override fun cancel() { cancels++ }
        }
        val player = object : com.loresuelvo.serviceprovider.domain.conversation.AudioPlayer {
            override val isPlaying = kotlinx.coroutines.flow.MutableStateFlow(false)
            override val currentPositionMillis = kotlinx.coroutines.flow.MutableStateFlow(0L)
            override fun play(url: String, startPositionMillis: Long) = Unit
            override fun seekTo(positionMillis: Long) = Unit
            override fun pause() = Unit
            override fun stop() { isPlaying.value = false }
        }
        val repository = object : com.loresuelvo.serviceprovider.domain.conversation.ConversationRepository {
            override suspend fun getConversations(): com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome = error("unused")
            override suspend fun getConversationById(conversationId: Int) = com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome.Success(
                com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail(42,
                    com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus.Active,
                    com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart(7, "Ana", "Pérez", null), emptyList(), 1))
            override suspend fun sendMessage(conversationId: Int, content: String): com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome { sends++; error("No lifecycle sends") }
            override suspend fun sendMediaMessage(conversationId: Int, media: List<MediaUpload>): com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome { sends++; error("No lifecycle sends") }
        }
        compose.runOnUiThread {
            model = com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationViewModel(
                androidx.lifecycle.SavedStateHandle(mapOf(com.loresuelvo.serviceprovider.ui.navigation.Route.Conversation.argument to 42)),
                com.loresuelvo.serviceprovider.domain.usecase.conversation.GetConversationByIdUseCase(repository),
                com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMessageUseCase(repository),
                com.loresuelvo.serviceprovider.domain.usecase.conversation.SendMediaMessageUseCase(repository),
                object : com.loresuelvo.serviceprovider.domain.conversation.MediaReader { override suspend fun read(uri: String): MediaUpload = error("No read on recreation") },
                recorder, player)
        }
        try {
            compose.setContent {
                com.loresuelvo.serviceprovider.ui.screens.conversation.ConversationAudioLifecycle(model::onConversationBackgrounded)
                MaterialTheme { Button(onClick = model::onStartRecording) { Text("Capture") } }
            }
            compose.onNodeWithText("Capture").performClick()
            assertEquals(1, starts)
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertTrue(cancels > 0)
            assertEquals(1, starts)
            assertEquals(0, sends)
            assertTrue((model.uiState.value as com.loresuelvo.serviceprovider.ui.screens.conversation.ProviderConversationUiState.Ready).recordingState is com.loresuelvo.serviceprovider.ui.screens.conversation.RecordingState.Idle)
        } finally {
            compose.runOnUiThread { androidx.lifecycle.ViewModelStore().apply { put("conversation", model) }.clear() }
        }
    }

    private class ControlledRegistry : ActivityResultRegistry() {
        var code = 0
        var launches = 0
        var contract: ActivityResultContract<*, *>? = null
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            code = requestCode
            launches++
            this.contract = contract
        }
    }
}
