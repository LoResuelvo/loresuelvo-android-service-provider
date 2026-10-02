package com.loresuelvo.serviceprovider.acceptance.messaging

import android.net.Uri
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.domain.conversation.CameraOutput
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.MediaReference
import com.loresuelvo.serviceprovider.ui.screens.conversation.ChatListItem
import com.loresuelvo.serviceprovider.ui.screens.conversation.ConversationImageLaunchers
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.MessageBubble
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Activity result registration with controlled native contract responses; no external apps. */
@RunWith(AndroidJUnit4::class)
class ConversationImageBoundaryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun native_gallery_delivers_three_uris_and_cancellation_does_not_attach() {
        val registry = ControlledRegistry()
        val delivered = mutableListOf<List<String>>()
        install(registry, { 42 }) { images, _ -> delivered += images }
        compose.onNodeWithText("Gallery").performClick()
        assertTrue(registry.contract is ActivityResultContracts.PickMultipleVisualMedia)
        compose.runOnIdle { registry.dispatchResult(registry.requestCode, listOf(Uri.parse("content://1"), Uri.parse("content://2"), Uri.parse("content://3"))) }
        assertEquals(listOf("content://1", "content://2", "content://3"), delivered.single())
        compose.onNodeWithText("Gallery").performClick()
        compose.runOnIdle { registry.dispatchResult(registry.requestCode, emptyList<Uri>()) }
        assertEquals(1, delivered.size)
        compose.onNodeWithText("Camera").performClick()
        compose.runOnIdle { registry.dispatchResult(registry.requestCode, false) }
        assertEquals(1, delivered.size)
    }

    @Test
    fun pending_camera_output_survives_activity_recreation() {
        val registry = ControlledRegistry()
        val delivered = mutableListOf<List<String>>()
        install(registry, { 42 }) { images, _ -> delivered += images }
        compose.onNodeWithText("Camera").performClick()
        assertTrue(registry.contract is ActivityResultContracts.TakePicture)
        assertEquals(Uri.parse("content://camera/output"), registry.input)
        compose.activityRule.scenario.recreate()
        install(registry, { 42 }) { images, _ -> delivered += images }
        compose.runOnIdle { registry.dispatchResult(registry.requestCode, true) }
        assertEquals(listOf("content://camera/output"), delivered.single())
    }

    @Test
    fun camera_result_cannot_cross_conversation_origin() {
        val registry = ControlledRegistry()
        val conversation = mutableIntStateOf(42)
        val delivered = mutableListOf<List<String>>()
        install(registry, { conversation.intValue }) { images, _ -> delivered += images }
        compose.onNodeWithText("Camera").performClick()
        compose.runOnIdle { conversation.intValue = 99 }
        compose.runOnIdle { registry.dispatchResult(registry.requestCode, true) }
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun confirmed_images_from_both_participants_open_zoom_viewer_and_close_to_same_bubble() {
        val images = (1..3).map { MediaReference.Image("$it", "file:///unavailable-image-$it.jpg", "image/jpeg", "$it.jpg") }
        compose.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column(Modifier.verticalScroll(rememberScrollState())) {
                    listOf(ConversationSender.Provider, ConversationSender.Consumer).forEachIndexed { index, sender ->
                        MessageBubble(ChatListItem.ServerConfirmed(ConversationMessage(index + 1, sender, "", 1,
                            media = images.first(), images = images)), {}, { _, _ -> }, {}, null, 0, false)
                    }
                }
            }
        }
        compose.onAllNodesWithContentDescription("3.jpg").assertCountEquals(2)
        for (participant in 1..2) {
            compose.onNodeWithTag("provider-message-image-$participant-2").performScrollTo().performClick()
            compose.onNodeWithTag("provider-image-viewer").assertIsDisplayed()
            compose.onNodeWithTag("provider-image-zoom-in").performClick()
            compose.onNodeWithTag("provider-image-zoom-out").assertIsEnabled().performClick()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("provider-image-viewer").assertDoesNotExist()
            compose.onNodeWithTag("provider-message-image-$participant-2").assertExists()
        }
    }

    private fun install(registry: ControlledRegistry, conversation: () -> Int, onImages: (List<String>, Int?) -> Unit) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                    override val activityResultRegistry = registry
                }) {
                    MaterialTheme {
                        ConversationImageLaunchers(conversation(), object : CameraOutput {
                            override fun createCameraOutputUri() = "content://camera/output"
                        }, { true }, onImages) { gallery, camera, _ ->
                            androidx.compose.foundation.layout.Column {
                                Button(onClick = gallery) { Text("Gallery") }
                                Button(onClick = camera) { Text("Camera") }
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private class ControlledRegistry : ActivityResultRegistry() {
        var requestCode = 0
        var contract: ActivityResultContract<*, *>? = null
        var input: Any? = null
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            this.requestCode = requestCode
            this.contract = contract
            this.input = input
        }
    }
}
