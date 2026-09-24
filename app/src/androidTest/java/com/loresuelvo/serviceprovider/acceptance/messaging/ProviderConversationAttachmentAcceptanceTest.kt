package com.loresuelvo.serviceprovider.acceptance.messaging

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.test.espresso.Espresso.onView
import android.view.KeyEvent
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import java.util.Calendar
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupConversationRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupCurrentAccountRepository
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupSessionStore
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupServiceProposalRepository
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
import com.loresuelvo.serviceprovider.ui.screens.conversation.PROPOSAL_FORM_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.PROPOSAL_DATE_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.PROPOSAL_TIME_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.PROPOSAL_DURATION_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.PROPOSAL_CONTINUE_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.PROPOSAL_CONFIRMATION_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_ATTACH_BUTTON_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_INPUT_FIELD_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CREATE_PROPOSAL_ROW_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_MEDIA_ATTACH_GALLERY_ROW_TAG
import com.loresuelvo.serviceprovider.ui.screens.messages.components.PROVIDER_MESSAGES_ROW_TAG_PREFIX
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.components.SingletonComponent
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import kotlinx.coroutines.CompletableDeferred
import androidx.lifecycle.Lifecycle
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome

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

    private val datePickerRoot = withDecorView(hasDescendant(isAssignableFrom(DatePicker::class.java)))
    private val timePickerRoot = withDecorView(hasDescendant(isAssignableFrom(TimePicker::class.java)))

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var sessionStore: ProviderSignupSessionStore
    private lateinit var currentAccountRepository: ProviderSignupCurrentAccountRepository
    private lateinit var conversationRepository: ProviderSignupConversationRepository
    private lateinit var proposalRepository: ProviderSignupServiceProposalRepository

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
        proposalRepository = entryPoint.proposalRepository()
        proposalRepository.created.clear()
        proposalRepository.pending = null

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

    @After
    fun tearDown() {
        proposalRepository.pending?.cancel()
        proposalRepository.pending = null
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

    @Test
    fun active_chat_action_opens_form_for_the_consumer() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).performClick()

        composeTestRule.onNodeWithTag(PROPOSAL_FORM_TAG).assertIsDisplayed()
        val context = composeTestRule.activity
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_consumer, "Ana Pérez")).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_amount)).assertIsDisplayed()
    }

    @Test
    fun closing_unsent_proposal_returns_to_same_chat_and_keeps_both_drafts() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).performTextInput("Chat draft")
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).performClick()
        val context = composeTestRule.activity
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_amount)).performTextInput("100,50")
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_reason)).performTextInput("Inspect sink")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PROPOSAL_FORM_TAG).assertIsDisplayed()
        assertFalse(composeTestRule.activity.window.decorView.rootWindowInsets
            ?.isVisible(android.view.WindowInsets.Type.ime()) ?: false)

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.provider_proposal_close)).performClick()
        composeTestRule.onAllNodesWithTag(PROPOSAL_FORM_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithText("Ana Pérez").assertIsDisplayed()
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Chat draft", substring = false)

        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_amount)).assertTextContains("100,50", substring = false)
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_reason)).assertTextContains("Inspect sink", substring = false)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(PROPOSAL_FORM_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithText("Ana Pérez").assertIsDisplayed()
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Chat draft", substring = false)
    }

    @Test
    fun valid_form_opens_review_on_same_conversation_without_sending() {
        openValidProposalReview()
        val context = composeTestRule.activity
        val insideConfirmation = hasAnyAncestor(hasTestTag(PROPOSAL_CONFIRMATION_TAG))
        composeTestRule.onNode(hasText(context.getString(R.string.provider_proposal_consumer, "Ana Pérez"))
            .and(insideConfirmation)).assertIsDisplayed()
        composeTestRule.onNode(hasText(context.getString(R.string.provider_proposal_review_amount, "100.5"))
            .and(insideConfirmation)).assertIsDisplayed()
        composeTestRule.onNode(hasText("Inspect sink").and(insideConfirmation)).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_confirm_send)).assertIsEnabled()
    }

    @Test
    fun confirmed_proposal_returns_to_chat_and_clears_form() {
        openValidProposalReview()
        val context = composeTestRule.activity
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_confirm_send)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(PROPOSAL_FORM_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_sent)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ana Pérez").assertIsDisplayed()
        org.junit.Assert.assertEquals(1, proposalRepository.created.size)
        org.junit.Assert.assertEquals(7, proposalRepository.created.single().consumerId)
        org.junit.Assert.assertEquals("100.5", proposalRepository.created.single().amountPesos)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_sent)).assertDoesNotExist()
        org.junit.Assert.assertEquals(1, proposalRepository.created.size)
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).performClick()
        composeTestRule.onNodeWithTag(PROPOSAL_FORM_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("100,50").assertDoesNotExist()
        composeTestRule.onNodeWithText("Inspect sink").assertDoesNotExist()
    }

    @Test
    fun proposal_form_survives_background_and_activity_recreation() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).performClick()
        val context = composeTestRule.activity
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_amount)).performTextInput("100,50")
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_reason)).performTextInput("Inspect sink")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeTestRule.waitForIdle()

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PROPOSAL_FORM_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_amount))
            .assertTextContains("100,50", substring = false)
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_reason))
            .assertTextContains("Inspect sink", substring = false)
        assertEquals(0, proposalRepository.created.size)
    }

    @Test
    fun pending_proposal_send_survives_activity_recreation_without_another_request() {
        openValidProposalReview()
        proposalRepository.pending = CompletableDeferred()
        val context = composeTestRule.activity
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_confirm_send)).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, proposalRepository.created.size)
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_sending)).assertIsDisplayed()
        assertEquals(1, proposalRepository.created.size)
        proposalRepository.pending?.complete(CreateServiceProposalOutcome.Created(9))
        composeTestRule.waitForIdle()
        composeTestRule.onAllNodesWithTag(PROPOSAL_FORM_TAG).assertCountEquals(0)
        assertEquals(1, proposalRepository.created.size)
    }

    private fun openValidProposalReview() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).performClick()
        composeTestRule.onNodeWithTag(PROVIDER_CREATE_PROPOSAL_ROW_TAG).performClick()
        val context = composeTestRule.activity
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_amount)).performTextInput("100,50")
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_reason)).performTextInput("Inspect sink")
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PROPOSAL_FORM_TAG).assertIsDisplayed()
        assertFalse(composeTestRule.activity.window.decorView.rootWindowInsets
            ?.isVisible(android.view.WindowInsets.Type.ime()) ?: false)

        val visit = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 3); set(Calendar.HOUR_OF_DAY, 11); set(Calendar.MINUTE, 0) }
        composeTestRule.onNodeWithTag(PROPOSAL_DATE_TAG).performClick()
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(datePickerRoot).perform(pickerAction("select visit date") { view ->
            (view as DatePicker).updateDate(visit.get(Calendar.YEAR), visit.get(Calendar.MONTH), visit.get(Calendar.DAY_OF_MONTH))
        })
        onView(withId(android.R.id.button1)).inRoot(datePickerRoot).perform(click())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        onView(isAssignableFrom(DatePicker::class.java)).check(doesNotExist())
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PROPOSAL_TIME_TAG).performClick()
        onView(isAssignableFrom(TimePicker::class.java)).inRoot(timePickerRoot).perform(pickerAction("select visit time") { view ->
            (view as TimePicker).apply { hour = 11; minute = 0 }
        })
        // Physical Samsung touch injection leaves this dialog open; invoke its native positive callback.
        onView(withId(android.R.id.button1)).inRoot(timePickerRoot)
            .perform(pickerAction("confirm visit time") { it.performClick() })
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_time) + ": 11:00").assertExists()
        onView(isAssignableFrom(TimePicker::class.java)).check(doesNotExist())
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(PROPOSAL_DURATION_TAG).performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithText(context.getString(R.string.provider_proposal_duration_minutes, 45))
            .performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag(PROPOSAL_CONTINUE_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(PROPOSAL_CONFIRMATION_TAG).assertIsDisplayed()
    }

    private fun pickerAction(description: String, change: (View) -> Unit): ViewAction = object : ViewAction {
        override fun getConstraints() = org.hamcrest.Matchers.any(View::class.java)
        override fun getDescription() = description
        override fun perform(uiController: UiController, view: View) {
            change(view)
            uiController.loopMainThreadUntilIdle()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProviderConversationAttachmentTestEntryPoint {

    fun sessionStore(): ProviderSignupSessionStore

    fun currentAccountRepository(): ProviderSignupCurrentAccountRepository

    fun conversationRepository(): ProviderSignupConversationRepository

    fun proposalRepository(): ProviderSignupServiceProposalRepository
}
