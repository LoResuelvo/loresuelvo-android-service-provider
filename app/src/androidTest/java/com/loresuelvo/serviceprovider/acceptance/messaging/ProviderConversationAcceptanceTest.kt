package com.loresuelvo.serviceprovider.acceptance.messaging

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupConversationRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupCurrentAccountRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupSessionStore
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.conversation.Conversation
import com.loresuelvo.serviceprovider.domain.conversation.ConversationCounterpart
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetail
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.domain.conversation.ConversationMessage
import com.loresuelvo.serviceprovider.domain.conversation.ConversationSender
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_TAG
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.PROVIDER_CONVERSATION_MESSAGES_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_INPUT_FIELD_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_MIC_BUTTON_TAG
import com.loresuelvo.serviceprovider.ui.screens.messages.components.PROVIDER_MESSAGES_ROW_TAG_PREFIX
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Acceptance smoke for the provider conversation detail surface
 * (US-A). Drives `Route.Conversation` through the existing
 * inbox → row tap → conversation flow and asserts the live
 * screen renders the counterpart's name, the messages list, the
 * chat input bar, and the visibility contract (bottom bar hidden
 * on the conversation destination).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProviderConversationAcceptanceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var sessionStore: ProviderSignupSessionStore
    private lateinit var currentAccountRepository: ProviderSignupCurrentAccountRepository
    private lateinit var conversationRepository: ProviderSignupConversationRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        val entryPoint = EntryPointAccessors.fromApplication(
            ApplicationProvider.getApplicationContext(),
            ProviderConversationTestEntryPoint::class.java,
        )
        sessionStore = entryPoint.sessionStore()
        currentAccountRepository = entryPoint.currentAccountRepository()
        conversationRepository = entryPoint.conversationRepository()

        currentAccountRepository.outcome = CurrentAccountOutcome.Success(
            CurrentAccount.Provider(
                id = 1,
                name = "Carlos",
                surname = "Gómez",
                email = "provider@example.com",
                category = Category(id = 1, name = "Plomería"),
                profilePhotoUrl = null,
            ),
        )

        conversationRepository.outcome = ConversationsOutcome.Success(
            listOf(
                Conversation(
                    id = 42,
                    status = ConversationStatus.Active,
                    counterpart = ConversationCounterpart(
                        id = 7,
                        name = "Ana",
                        surname = "Pérez",
                        profilePhotoUrl = null,
                    ),
                    lastMessage = null,
                    updatedOnEpochMillis = 1L,
                ),
            ),
        )

        conversationRepository.detailOutcome = ConversationDetailOutcome.Success(
            detail = ConversationDetail(
                id = 42,
                status = ConversationStatus.Active,
                counterpart = ConversationCounterpart(
                    id = 7,
                    name = "Ana",
                    surname = "Pérez",
                    profilePhotoUrl = null,
                ),
                messages = listOf(
                    ConversationMessage(
                        id = 1,
                        sender = ConversationSender.Consumer,
                        content = "Hola, ¿podés ayudarme?",
                        createdOnEpochMillis = 1L,
                    ),
                ),
                updatedOnEpochMillis = 1L,
            ),
        )

        sessionStore.saveSession(
            AuthSession(
                user = User("auth0|provider-device", "provider@example.com"),
                accessToken = "device-access-token",
            ),
        )
    }

    @Test
    fun opens_conversation_and_renders_messages_and_input_bar() {
        composeTestRule.waitForIdle()

        // Tap Messages on the bottom bar.
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path)
            .performClick()
        composeTestRule.waitForIdle()

        // Tap the inbox row to open the conversation.
        val rowTag = PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42
        composeTestRule.onNodeWithTag(rowTag).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Header shows the counterpart full name.
        composeTestRule.onNodeWithText("Ana Pérez").assertIsDisplayed()

        // Messages list is rendered with the existing bubble.
        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG)
            .assertIsDisplayed()

        // Chat input bar is rendered and the trailing-slot action
        // is on screen. With a blank input + no staged media the
        // trailing slot swaps to the mic affordance (US-C, commit
        // 0553efe), so we assert MicButton here. The Send button
        // only appears once the user types or attaches something.
        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_MIC_BUTTON_TAG)
            .assertIsDisplayed()

        // Bottom bar hidden on the conversation destination.
        composeTestRule.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_TAG).assertCountEquals(0)

        // Back button returns to the inbox without crashing.
        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.getString(R.string.provider_conversation_close),
            )
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(rowTag).assertIsDisplayed()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProviderConversationTestEntryPoint {

    fun sessionStore(): ProviderSignupSessionStore

    fun currentAccountRepository(): ProviderSignupCurrentAccountRepository

    fun conversationRepository(): ProviderSignupConversationRepository
}
