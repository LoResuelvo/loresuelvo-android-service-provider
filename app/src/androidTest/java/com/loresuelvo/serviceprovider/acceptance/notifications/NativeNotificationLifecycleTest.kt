package com.loresuelvo.serviceprovider.acceptance.notifications

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.auth.*
import com.loresuelvo.serviceprovider.domain.conversation.*
import com.loresuelvo.serviceprovider.domain.notifications.InstallationResult
import com.loresuelvo.serviceprovider.domain.paymentaccount.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_INPUT_FIELD_TAG
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX
import com.loresuelvo.serviceprovider.ui.screens.profile.*
import com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.CompletableDeferred
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NativeNotificationLifecycleTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private lateinit var native: NativeNotificationHarness
    @Before fun setup() { hilt.inject(); native = NativeNotificationHarness(compose); native.launch(); native.waitRegistered() }
    @After fun teardown() { if (::native.isInitialized) native.close() }

    @Test fun resumed_current_chat_refreshes_authoritative_data_without_alert_and_preserves_draft_and_reading_anchor() {
        val messages = (1..60).map { ConversationMessage(it, ConversationSender.Consumer, "Historical message $it", it.toLong()) }
        native.conversations.detailOutcome = ConversationDetailOutcome.Success(native.detail(messages))
        native.deliver(native.payload()); native.tap()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).performTextInput("Reply still being written")
        Espresso.closeSoftKeyboard()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_MESSAGES_TAG).performScrollToIndex(0)
        val anchor = compose.onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + "1").fetchSemanticsNode().boundsInRoot.top
        val calls = native.conversations.detailCalls
        native.conversations.detailOutcome = ConversationDetailOutcome.Success(native.detail(messages +
            ConversationMessage(61, ConversationSender.Consumer, "Fresh authorized incoming message", 61)))
        native.deliver(native.payload(event = "message:161:7"))
        compose.waitUntil(10_000) { native.conversations.detailCalls > calls }
        compose.waitForIdle()
        assertTrue(native.notices().isEmpty())
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Reply still being written")
        compose.onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + "1").assertIsDisplayed()
        assertEquals(anchor, compose.onNodeWithTag(PROVIDER_MESSAGE_BUBBLE_TAG_PREFIX + "1").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag(PROVIDER_CONVERSATION_NEW_MESSAGE_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithText("Fresh authorized incoming message").assertIsDisplayed()
        native.manager.cancelAll()
        native.deliver(native.payload(resourceId = 43, event = "message:162:7")); native.waitNotice()
        native.manager.cancelAll()
        native.background()
        native.deliver(native.payload(event = "message:163:7")); native.waitNotice()
        native.resume()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertTextContains("Reply still being written")
    }

    @Test fun confirmed_logout_cancels_native_alerts_and_taps_before_suspended_removal_then_finishes_offline() {
        native.deliver(native.payload())
        val pending = native.waitNotice().notification.contentIntent
        val oldPayload = native.payload(event = "message:199:7")
        native.installations.removalGate = CompletableDeferred()
        native.installations.result = InstallationResult.TransientFailure
        confirmLogout()
        compose.waitUntil(3_000) { native.installations.removalStarted }
        assertNotNull(native.sessions.getSession())
        assertTrue(native.notices().isEmpty())
        native.deliver(oldPayload); assertTrue(native.notices().isEmpty())
        native.tap(pending)
        compose.onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG).assertDoesNotExist()
        assertNull(native.entry.local().target.value)
        native.installations.removalGate!!.complete(Unit)
        compose.onNodeWithText(native.context.getString(R.string.welcome_login)).assertIsDisplayed()
        assertNull(native.sessions.getSession())
    }

    @Test fun offline_logout_and_new_provider_login_reject_old_native_notice_and_pending_tap_while_current_notice_remains_usable() {
        native.deliver(native.payload())
        val oldTap = native.waitNotice().notification.contentIntent
        val oldPayload = native.payload(event = "message:201:7")
        native.installations.result = InstallationResult.TransientFailure
        confirmLogout()
        compose.onNodeWithText(native.context.getString(R.string.welcome_login)).assertIsDisplayed()
        native.installations.result = InstallationResult.Applied
        compose.runOnIdle {
            native.setProvider(8)
            native.conversations.detailOutcome = ConversationDetailOutcome.Success(native.detail(listOf(
                ConversationMessage(99, ConversationSender.Consumer, "Current account authorized message", 99))))
            native.sessions.saveSession(AuthSession(User("provider-current", "new@example.test"), "current-synthetic-session"))
        }
        native.waitRegistered(); native.assertHome()
        native.deliver(native.payload(event = "message:202:8"))
        val current = native.waitNotice()
        native.deliver(oldPayload)
        assertEquals(current.postTime, native.notices().single().postTime)
        native.tap(oldTap); native.assertHome()
        assertEquals("provider-current", native.sessions.getSession()!!.user.id)
        assertEquals(current.postTime, native.notices().single().postTime)
        native.tap(current.notification.contentIntent)
        compose.onNodeWithText("Current account authorized message").assertIsDisplayed()
    }

    @Test fun pending_cold_payment_return_survives_warm_notification_intent_and_recreation_until_provider_entry_completes() {
        native.deliver(native.payload())
        val tap = native.waitNotice().notification.contentIntent
        native.closeActivity()
        native.account.pending = CompletableDeferred()
        native.entry.payment().outcome = PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.CONNECTED))
        native.launch(paymentReturn())
        native.tap(tap) // Actual onNewIntent while /me is pending, with a genuine return URL already saved.
        native.recreate()
        native.account.pending!!.complete(Unit)
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertIsDisplayed()
        compose.onNodeWithText(native.context.getString(R.string.provider_profile_connection_connected)).performScrollTo().assertIsDisplayed()
        native.assertOneActivity()
        native.recreate()
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertIsDisplayed()
    }

    @Test fun warm_payment_return_is_consumed_once_and_later_notification_recreation_keeps_the_conversation() {
        native.entry.payment().outcome = PaymentAccountStatusOutcome.Success(PaymentAccountStatus(ConnectionStatus.CONNECTED))
        native.context.startActivity(paymentReturn().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertIsDisplayed()
        compose.onNodeWithText(native.context.getString(R.string.provider_profile_connection_connected)).performScrollTo().assertIsDisplayed()
        native.deliver(native.payload()); native.tap()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG).assertIsDisplayed()
        native.recreate()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG).assertIsDisplayed()
        native.assertOneActivity()
        Espresso.pressBack()
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertIsDisplayed()
    }

    private fun confirmLogout() {
        native.openProfile()
        compose.onNodeWithTag(PROVIDER_LOGOUT_ACTION_TAG).performScrollTo()
        val range = compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
            scroll(0f, range.maxValue())
        }
        compose.waitForIdle()
        val action = compose.onNodeWithTag(PROVIDER_LOGOUT_ACTION_TAG).fetchSemanticsNode().boundsInRoot
        val bar = compose.onNodeWithTag(PROVIDER_BOTTOM_BAR_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue("Logout action must be above the navigation overlay", action.bottom <= bar.top)
        compose.onNodeWithTag(PROVIDER_LOGOUT_ACTION_TAG).assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(PROVIDER_LOGOUT_CONFIRM_TAG).fetchSemanticsNodes().size == 1 }
        compose.onNodeWithTag(PROVIDER_LOGOUT_CONFIRM_TAG).assertIsDisplayed().performClick()
    }

    private fun paymentReturn() = Intent(native.context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("https://return.example.test/provider/register/mercado-pago?result=success")
    }
}
