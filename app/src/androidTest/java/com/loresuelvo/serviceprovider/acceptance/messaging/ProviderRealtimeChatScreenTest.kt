package com.loresuelvo.serviceprovider.acceptance.messaging

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.*
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/** Device Compose geometry/actions under test-owned locale/font contexts; no device setting changes. */
@RunWith(AndroidJUnit4::class)
class ProviderRealtimeChatScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val state = mutableStateOf(ready())
    private var played: Pair<String, String>? = null
    private var paused = 0
    private var retried = 0
    private val assets = mutableListOf<File>()

    @After fun cleanAssets() { assets.forEach(File::delete) }

    private fun ready(): ProviderConversationUiState.Ready {
        val messages = (1..30).map { ConversationMessage(it, ConversationSender.Consumer, "History $it", it.toLong()) }
        return ProviderConversationUiState.Ready(ConversationDetail(42, ConversationStatus.Active,
            ConversationCounterpart(7, "Ana", "Perez", null), messages, 30), messages.map(ChatListItem::ServerConfirmed), "Unsent reply", false)
    }
    private fun show(locale: String = "es", fontScale: Float = 1f) {
        val configuration = Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) }
        val localizedContext = compose.activity.createConfigurationContext(configuration)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides localizedContext, LocalDensity provides Density(density.density, fontScale)) {
                LoresuelvoTheme {
                    ProviderConversationScreen(state.value, onPromptChange = {}, onSendClick = {}, onRetrySendFailedBubble = {},
                        onRetryLoad = { retried++ }, onMediaPicked = {}, onClearStagedMedia = {}, onClose = {},
                        onPlayAudio = { key, url -> played = key to url; state.value = state.value.copy(playingMediaKey = key, isPlaying = true) },
                        onPauseAudio = { paused++; state.value = state.value.copy(isPlaying = false) })
                }
            }
        }
        compose.waitForIdle()
    }
    private fun append(id: Int, text: String = "Arrival $id") {
        compose.runOnIdle { state.value = state.value.copy(items = state.value.items +
            ChatListItem.ServerConfirmed(ConversationMessage(id, ConversationSender.Consumer, text, id.toLong()))) }
    }
    private fun list() = compose.onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG)
    private fun bubble(id: Int) = compose.onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + id)

    @Test fun spanish_notice_and_retry_remain_accessible_at_enlarged_text() { verifyAccessible("es", "Nuevo mensaje") }
    @Test fun english_notice_and_retry_remain_accessible_at_enlarged_text() { verifyAccessible("en", "New message") }

    private fun verifyAccessible(locale: String, label: String) {
        show(locale, 1.6f)
        list().performScrollToIndex(8)
        compose.waitForIdle()
        val anchor = bubble(9).fetchSemanticsNode().boundsInRoot
        append(31); append(32)
        compose.waitForIdle()
        assertEquals(anchor, bubble(9).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag(PROVIDER_CONVERSATION_NEW_MESSAGE_TAG).assertTextEquals(label)
            .assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).performClick()
        bubble(32).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_NEW_MESSAGE_TAG).assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(refreshFailure = ConversationDetailOutcome.Failure.Network(java.io.IOException("offline"))) }
        compose.onNodeWithTag(PROVIDER_CONVERSATION_REFRESH_RETRY_TAG).assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertHasClickAction().performClick()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Unsent reply")
        assertEquals(1, retried)
        val localized = compose.activity.createConfigurationContext(Configuration(compose.activity.resources.configuration).apply { setLocale(Locale.forLanguageTag(locale)) })
        compose.onNodeWithText(localized.getString(R.string.provider_chat_refresh_error)).assertIsDisplayed()
    }

    @Test fun tall_latest_arrival_scrolls_to_measured_end_and_media_actions_open_local_content() {
        val imageFile = File(compose.activity.cacheDir, "realtime-image.png").also(assets::add)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val image = MediaReference.Image("photo", imageFile.toURI().toString(), "image/png", "photo.png")
        val audio = MediaReference.Audio("voice", "file:///controlled-voice.webm", "audio/webm", "voice.webm", 2000)
        show()
        append(31, (1..65).joinToString("\n") { "Long incoming line $it" })
        compose.waitForIdle()
        val bottomGap = list().getUnclippedBoundsInRoot().bottom.value - bubble(31).getUnclippedBoundsInRoot().bottom.value
        assertTrue(bottomGap in 0f..16f)
        compose.runOnIdle {
            state.value = state.value.copy(items = state.value.items + listOf(
                ChatListItem.ServerConfirmed(ConversationMessage(32, ConversationSender.Consumer, "", 32, media = image, images = listOf(image))),
                ChatListItem.ServerConfirmed(ConversationMessage(33, ConversationSender.Consumer, "", 33, ConversationMessageKind.Audio, audio))))
        }
        compose.onNodeWithTag(PROVIDER_MESSAGE_AUDIO_PLAY_TAG_PREFIX + 33).assertIsDisplayed().performClick()
        assertEquals("33" to audio.url, played)
        compose.onNodeWithTag(PROVIDER_MESSAGE_AUDIO_PLAY_TAG_PREFIX + 33).performClick()
        assertEquals(1, paused)
        list().performScrollToIndex(31)
        compose.onNodeWithTag(PROVIDER_MESSAGE_IMAGE_TAG_PREFIX + "32").performScrollTo().performClick()
        compose.onNodeWithTag("provider-image-viewer").assertIsDisplayed()
        compose.onNodeWithTag("provider-image-zoom-in").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("provider-image-viewer").assertDoesNotExist()
    }
}
