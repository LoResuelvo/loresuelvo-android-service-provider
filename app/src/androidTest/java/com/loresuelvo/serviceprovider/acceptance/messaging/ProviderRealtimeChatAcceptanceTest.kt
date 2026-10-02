package com.loresuelvo.serviceprovider.acceptance.messaging

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.acceptance.auth.*
import com.loresuelvo.serviceprovider.domain.account.*
import com.loresuelvo.serviceprovider.domain.activity.*
import com.loresuelvo.serviceprovider.domain.auth.*
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.conversation.*
import com.loresuelvo.serviceprovider.domain.realtime.*
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.screens.conversation.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.*
import com.loresuelvo.serviceprovider.ui.screens.messages.components.PROVIDER_MESSAGES_ROW_TAG_PREFIX
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.*
import dagger.hilt.components.SingletonComponent
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first

/** Real Activity/NavHost/lifecycle with controlled repositories and one shared realtime owner. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ProviderRealtimeChatAcceptanceTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var ports: ProviderRealtimeChatEntryPoint
    private val accountA = AuthSession(User("provider-a", "a@example.test"), "token-a")
    private val counterpart = ConversationCounterpart(7, "Ana", "Perez", null)

    @Before fun setUp() {
        hilt.inject()
        ports = EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), ProviderRealtimeChatEntryPoint::class.java)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ports.client().reset()
            ports.sessions().clearSession()
            ports.account().outcome = CurrentAccountOutcome.Success(CurrentAccount.Provider(
                1, "Carlos", "Gomez", "a@example.test", Category(1, "Plumbing"), null))
            ports.requests().requests = emptyList()
            ports.requests().acceptance = AcceptJobRequestOutcome.Failure.Invalid
            ports.conversations().pendingSend = null
            ports.conversations().sentTexts.clear()
            arrangeHistory()
            ports.sessions().saveSession(accountA)
        }
    }

    @After fun tearDown() {
        scenario?.close()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ports.conversations().pendingSend?.cancel()
            ports.conversations().pendingSend = null
            ports.sessions().clearSession()
            ports.requests().requests = emptyList()
            ports.requests().acceptance = AcceptJobRequestOutcome.Failure.Invalid
            ports.client().reset()
        }
    }

    private fun arrangeHistory(status: ConversationStatus = ConversationStatus.Active, count: Int = 30) {
        val messages = (1..count).map { ConversationMessage(it, ConversationSender.Consumer, "History $it", it.toLong()) }
        ports.conversations().detailOutcome = ConversationDetailOutcome.Success(ConversationDetail(42, status, counterpart, messages, count.toLong()))
        ports.conversations().outcome = ConversationsOutcome.Success(listOf(Conversation(42, status, counterpart, messages.last(), count.toLong())))
    }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java); compose.waitForIdle() }
    private fun openInboxChat() {
        launch()
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        compose.onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42).performClick()
        compose.waitForIdle()
    }
    private fun append(id: Int, deliver: Boolean = true, session: AuthSession = accountA) {
        compose.runOnIdle {
            val message = ConversationMessage(id, ConversationSender.Consumer, "Arrival $id", id.toLong())
            val detail = (ports.conversations().detailOutcome as ConversationDetailOutcome.Success).detail
            ports.conversations().detailOutcome = ConversationDetailOutcome.Success(detail.copy(messages = detail.messages + message, updatedOnEpochMillis = id.toLong()))
            ports.conversations().outcome = ConversationsOutcome.Success(listOf(Conversation(42, detail.status, counterpart, message, id.toLong())))
            if (deliver) ports.client().deliver(session, ProviderEvent.MessageCreated(42, message))
        }
        compose.waitForIdle()
    }
    private fun bubble(id: Int) = compose.onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + id)
    private fun list() = compose.onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG)
    private fun string(id: Int): String {
        var result = ""
        scenario!!.onActivity { result = it.getString(id) }
        return result
    }

    @Test fun inbox_arrival_follows_bottom_then_preserves_history_draft_and_latest_action() {
        openInboxChat()
        append(31)
        bubble(31).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_NEW_MESSAGE_TAG).assertDoesNotExist()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).performTextInput("Unsent reply")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        list().performScrollToIndex(8)
        compose.waitForIdle()
        val anchor = bubble(9).fetchSemanticsNode().boundsInRoot
        append(32); append(33)
        assertEquals(anchor, bubble(9).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Unsent reply")
        compose.onAllNodesWithTag(PROVIDER_CONVERSATION_NEW_MESSAGE_TAG).assertCountEquals(1)
        compose.onNodeWithTag(PROVIDER_CONVERSATION_NEW_MESSAGE_TAG).assertHasClickAction().performClick()
        bubble(33).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_NEW_MESSAGE_TAG).assertDoesNotExist()
        list().performScrollToIndex(31)
        bubble(32).assertExists()
    }

    @Test fun failed_recovery_retry_preserves_draft_and_recovered_inbox() {
        openInboxChat()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).performTextInput("Retained reply")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        append(31, deliver = false)
        lateinit var recovered: ConversationDetailOutcome
        compose.runOnIdle {
            recovered = ports.conversations().detailOutcome
            ports.conversations().detailOutcome = ConversationDetailOutcome.Failure.Network(java.io.IOException("offline"))
            ports.client().state.value = RealtimeState(accountA, RealtimeState.Connection.Retrying)
        }
        compose.runOnIdle { ports.client().state.value = RealtimeState(accountA, RealtimeState.Connection.Connected) }
        compose.onNodeWithTag(PROVIDER_CONVERSATION_REFRESH_RETRY_TAG).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Retained reply")
        // Refresh feedback reduces the viewport; retained history must remain readable.
        list().performScrollToIndex(29)
        bubble(30).assertIsDisplayed()
        compose.runOnIdle { ports.conversations().detailOutcome = recovered }
        compose.onNodeWithTag(PROVIDER_CONVERSATION_REFRESH_RETRY_TAG).performClick()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_REFRESH_TAG).assertDoesNotExist()
        bubble(31).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Retained reply")
        compose.onNodeWithTag(PROVIDER_CONVERSATION_BACK_TAG).performClick()
        compose.onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42).assertIsDisplayed()
        compose.onNodeWithText("Arrival 31").assertIsDisplayed()
    }

    @Test fun foreground_recovers_missed_activity_and_recreation_keeps_single_owner_and_draft() {
        openInboxChat()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).performTextInput("Session draft")
        scenario!!.moveToState(Lifecycle.State.CREATED)
        runBlocking { withTimeout(5_000) { ports.client().state.first { it.connection == RealtimeState.Connection.Stopped } } }
        scenario!!.onActivity { assertEquals(0, ports.client().active) }
        scenario!!.onActivity {
            val detail = (ports.conversations().detailOutcome as ConversationDetailOutcome.Success).detail
            ports.conversations().detailOutcome = ConversationDetailOutcome.Success(detail.copy(messages = detail.messages +
                ConversationMessage(31, ConversationSender.Consumer, "Missed while away", 31)))
        }
        scenario!!.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        bubble(31).assertIsDisplayed()
        scenario!!.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Session draft")
        bubble(31).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, ports.client().active); assertEquals(1, ports.client().maximumActive) }
    }

    @Test fun logout_and_other_login_remove_old_history_and_allow_current_inbox() {
        openInboxChat()
        compose.runOnIdle { ports.sessions().clearSession() }
        bubble(30).assertDoesNotExist()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertDoesNotExist()
        compose.runOnIdle {
            val other = Conversation(43, ConversationStatus.Active, ConversationCounterpart(8, "Bruno", "Perez", null), null, 1)
            ports.conversations().outcome = ConversationsOutcome.Success(listOf(other))
            ports.sessions().saveSession(AuthSession(User("provider-b", "b@example.test"), "token-b"))
        }
        compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_ITEM_PREFIX + Route.Messages.path).performClick()
        compose.onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 43).assertIsDisplayed()
        compose.runOnIdle { ports.client().deliver(accountA, ProviderEvent.MessageCreated(42,
            ConversationMessage(99, ConversationSender.Consumer, "Private previous account", 99))) }
        compose.onNodeWithText("Private previous account").assertDoesNotExist()
        compose.onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 42).assertDoesNotExist()
        compose.onNodeWithTag(PROVIDER_MESSAGES_ROW_TAG_PREFIX + 43).assertIsDisplayed()
    }

    @Test fun pending_entry_receives_updates_and_disables_response_until_accepted() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync { arrangeHistory(ConversationStatus.Pending) }
        openInboxChat()
        append(31)
        bubble(31).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_PENDING_TAG).assertTextEquals(string(R.string.provider_chat_accept_to_reply)).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROVIDER_CHAT_ATTACH_BUTTON_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROVIDER_CHAT_MIC_BUTTON_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(PROVIDER_CHAT_SEND_BUTTON_TAG).assertDoesNotExist()
        compose.runOnIdle { assertTrue(ports.conversations().sentTexts.isEmpty()) }
    }

    @Test fun accepted_request_entry_uses_same_realtime_conversation() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ports.requests().requests = listOf(JobRequest(7, "Ana Perez", "Pipe repair", "Leaking pipe"))
            ports.requests().acceptance = AcceptJobRequestOutcome.Success(7, 42)
        }
        launch()
        compose.onNodeWithText(string(R.string.provider_home_view_request)).performScrollTo().performClick()
        compose.onNodeWithText(string(R.string.provider_job_request_continue_conversation)).performScrollTo().performClick()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertIsEnabled()
        append(31)
        bubble(31).assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, ports.client().maximumActive) }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProviderRealtimeChatEntryPoint {
    fun sessions(): ProviderSignupSessionStore
    fun account(): ProviderSignupCurrentAccountRepository
    fun conversations(): ProviderSignupConversationRepository
    fun requests(): ProviderSignupJobRequestRepository
    fun client(): ProviderRealtimeTestClient
}
