package com.loresuelvo.serviceprovider.acceptance.messaging

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
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
import com.loresuelvo.serviceprovider.domain.conversation.MediaReference
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_TAG
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.PROVIDER_CONVERSATION_BACK_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.PROVIDER_CONVERSATION_MESSAGES_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_ATTACH_BUTTON_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_MEDIA_ATTACH_GALLERY_ROW_TAG
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
 * after the US-B image attach UI landed. Drives
 * `Route.Conversation` through the inbox → row tap path and
 * asserts the attach button is rendered on the chat composer.
 * The full image pick → preview → send round-trip is exercised by
 * the JVM VM tests (the gallery / camera launchers need a live
 * Activity result registry that the JVM tests stub out); this
 * acceptance test covers the integration surface — provider chat
 * thread is wired, attach affordance is reachable, bottom bar
 * stays hidden on the conversation destination.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProviderConversationAttachmentAcceptanceTest {

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
            ProviderConversationAttachmentTestEntryPoint::class.java,
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
                        content = "Hola",
                        createdOnEpochMillis = 1L,
                        media = MediaReference.Image(
                            id = "file-uuid-1",
                            url = "https://example.test/kitchen.jpg",
                            mimeType = "image/jpeg",
                            originalName = "kitchen.jpg",
                        ),
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
    fun opens_conversation_with_image_bubble_and_attach_button() {
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path)
            .performClick()
        composeTestRule.waitForIdle()

        val rowTag = PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42
        composeTestRule.onNodeWithTag(rowTag).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Ana Pérez").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG)
            .assertIsDisplayed()

        composeTestRule.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_TAG).assertCountEquals(0)

        composeTestRule
            .onNodeWithTag(PROVIDER_CONVERSATION_BACK_TAG)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(rowTag).assertIsDisplayed()
    }

    @Test
    fun attach_button_opens_bottom_sheet_with_gallery_row() {
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(PROVIDER_MEDIA_ATTACH_GALLERY_ROW_TAG)
            .assertIsDisplayed()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProviderConversationAttachmentTestEntryPoint {

    fun sessionStore(): ProviderSignupSessionStore

    fun currentAccountRepository(): ProviderSignupCurrentAccountRepository

    fun conversationRepository(): ProviderSignupConversationRepository
}
