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
import com.loresuelvo.serviceprovider.domain.conversation.ConversationStatus
import com.loresuelvo.serviceprovider.domain.conversation.ConversationsOutcome
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_TAG
import com.loresuelvo.serviceprovider.ui.navigation.Route
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

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProviderMessagesNavigationAcceptanceTest {

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
            ProviderMessagesNavigationTestEntryPoint::class.java,
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
                    updatedOnEpochMillis = 1_000L,
                ),
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
    fun opens_conversation_and_returns_to_the_retained_messages_inbox() {
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path)
            .performClick()
        composeTestRule.waitForIdle()

        val rowTag = PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42
        composeTestRule.onNodeWithTag(rowTag).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.provider_conversation_title))
            .assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(PROVIDER_BOTTOM_BAR_TAG).assertCountEquals(0)

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
interface ProviderMessagesNavigationTestEntryPoint {

    fun sessionStore(): ProviderSignupSessionStore

    fun currentAccountRepository(): ProviderSignupCurrentAccountRepository

    fun conversationRepository(): ProviderSignupConversationRepository
}
