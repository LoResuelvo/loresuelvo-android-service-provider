package com.loresuelvo.serviceprovider.ui.screens.messages

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessageKind
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
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
                                    content = "Hola, ¿podés ayudarme?",
                                    kind = ConversationMessageKind.Text,
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
}
