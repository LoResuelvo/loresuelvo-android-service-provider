package com.loresuelvo.serviceprovider.acceptance.notifications

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.activity.*
import com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome
import com.loresuelvo.serviceprovider.platform.notifications.AndroidNotificationDisplay
import com.loresuelvo.serviceprovider.ui.screens.conversation.*
import com.loresuelvo.serviceprovider.ui.screens.conversation.components.PROVIDER_CHAT_INPUT_FIELD_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NativeNotificationNavigationTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private lateinit var native: NativeNotificationHarness
    @Before fun setup() { hilt.inject(); native = NativeNotificationHarness(compose); native.launch(); native.waitRegistered() }
    @After fun teardown() { if (::native.isInitialized) native.close() }

    @Test fun real_service_posts_private_generic_content_on_two_native_channels_and_ignores_replays_expiry_or_bad_binding() {
        val payload = native.payload()
        native.deliver(payload)
        val first = native.waitNotice()
        val notification = first.notification
        assertEquals("Nuevo mensaje", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Tenés un nuevo mensaje en LoResuelvo.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.smallIcon)
        assertEquals(notification.extras.getCharSequence(Notification.EXTRA_TEXT), notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(native.context.packageName, notification.contentIntent.creatorPackage)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(notification.contentIntent.isImmutable)
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals(AndroidNotificationDisplay.MESSAGES_CHANNEL, notification.channelId)
            for (id in listOf(AndroidNotificationDisplay.MESSAGES_CHANNEL, AndroidNotificationDisplay.SERVICES_CHANNEL)) {
                assertEquals(NotificationManager.IMPORTANCE_DEFAULT, native.manager.getNotificationChannel(id).importance)
            }
        }
        native.deliver(payload) // Fresh SDK message ID, same persisted business event ID.
        assertEquals(1, native.notices().size)
        assertEquals(first.postTime, native.notices().single().postTime)
        native.deliver(native.payload(event = "message:124:7", expires = System.currentTimeMillis() - 1000))
        native.deliver(native.payload(event = "message:125:7") + ("binding_id" to "10000000-0000-4000-8000-000000000099"))
        assertEquals(1, native.notices().size)
        assertEquals(1, native.store.read().handled.size)
        native.manager.cancelAll()
        native.deliver(native.payload(NativeNotificationHarness.ACCEPTED, 55))
        val service = native.waitNotice().notification
        if (Build.VERSION.SDK_INT >= 26) assertEquals(AndroidNotificationDisplay.SERVICES_CHANNEL, service.channelId)
        assertEquals("Propuesta aceptada", service.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Se confirmó una contratación.", service.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test fun configured_firebase_auto_init_stays_disabled_and_real_service_token_callback_reads_synthetic_current_token() {
        assertFalse(native.context.getSharedPreferences("com.google.firebase.messaging", android.content.Context.MODE_PRIVATE).getBoolean("auto_init", true))
        if (com.google.firebase.FirebaseApp.getApps(native.context).any { it.name == com.google.firebase.FirebaseApp.DEFAULT_APP_NAME }) {
            assertFalse(com.google.firebase.messaging.FirebaseMessaging.getInstance().isAutoInitEnabled)
        }
        val binding = native.store.read().binding!!.id
        native.entry.token().current = "current-synthetic-renewed-token"
        native.deliverTokenCallback()
        compose.waitUntil(10_000) { native.store.read().registrationToken == "current-synthetic-renewed-token" }
        assertEquals(binding, native.store.read().binding!!.id)
        assertTrue(native.store.read().binding!!.acknowledged)
    }

    @Test fun immutable_pending_intent_opens_cold_conversation_recreates_and_back_does_not_duplicate_or_replay() {
        native.deliver(native.payload())
        val pending = native.waitNotice().notification.contentIntent
        native.closeActivity()
        native.tap(pending)
        compose.waitUntil(10_000) { compose.onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG).isDisplayed() }
        compose.onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG).assertIsDisplayed()
        compose.onNodeWithText("Ana Perez").assertIsDisplayed()
        native.assertOneActivity()
        native.recreate()
        compose.onAllNodesWithTag(PROVIDER_CONVERSATION_READY_TAG).assertCountEquals(1)
        Espresso.pressBack(); native.assertHome()
        native.tap(pending)
        native.assertHome()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG).assertDoesNotExist()
        native.assertOneActivity()
    }

    @Test fun warm_message_tap_and_cold_or_warm_service_taps_use_current_authorized_details_with_one_back_destination() {
        native.deliver(native.payload()); native.tap()
        compose.waitUntil(10_000) { compose.onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG).isDisplayed() }
        compose.onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG).assertIsDisplayed()
        Espresso.pressBack(); native.assertHome()
        listOf(NativeNotificationHarness.ACCEPTED, NativeNotificationHarness.REMINDER, NativeNotificationHarness.PAID).forEachIndexed { index, type ->
            native.manager.cancelAll()
            native.deliver(native.payload(type, 55))
            val pending = native.waitNotice().notification.contentIntent
            native.orders.detail = WorkOrderDetailOutcome.Success(native.order(if (type == NativeNotificationHarness.PAID) WorkOrderStatus.Paid else WorkOrderStatus.Scheduled))
            if (index != 1) native.closeActivity()
            native.tap(pending)
            compose.waitUntil(10_000) { compose.onNodeWithText("Current authorized service detail").isDisplayed() }
            compose.onNodeWithText("Current authorized service detail").assertIsDisplayed()
            if (type == NativeNotificationHarness.PAID) compose.onNodeWithText(native.context.getString(R.string.provider_turns_status_paid)).assertExists()
            val calls = native.orders.detailCalls
            assertTrue(calls > 0)
            native.recreate()
            compose.onNodeWithText("Current authorized service detail").assertIsDisplayed()
            native.assertOneActivity()
            Espresso.pressBack(); native.assertHome()
            native.tap(pending); native.assertHome()
        }
    }

    @Test fun authorized_destination_errors_render_real_retry_missing_and_forbidden_screens_without_private_content() {
        listOf(WorkOrderDetailOutcome.Failure.Network(java.io.IOException("private offline diagnostic")),
            WorkOrderDetailOutcome.Failure.NotFound, WorkOrderDetailOutcome.Failure.Forbidden).forEachIndexed { index, failure ->
            native.manager.cancelAll()
            native.orders.detail = failure
            native.deliver(native.payload(NativeNotificationHarness.ACCEPTED, 55, event = "notification:service_proposal_accepted:${200 + index}"))
            native.tap()
            val resource = when (failure) {
                is WorkOrderDetailOutcome.Failure.Network -> R.string.provider_order_detail_network_error
                WorkOrderDetailOutcome.Failure.NotFound -> R.string.provider_order_detail_missing
                else -> R.string.provider_order_detail_forbidden
            }
            compose.waitUntil(10_000) { compose.onNodeWithText(native.context.getString(resource)).isDisplayed() }
            compose.onNodeWithText(native.context.getString(resource)).assertIsDisplayed()
            compose.onNodeWithText("Current authorized service detail").assertDoesNotExist()
            compose.onNodeWithText("private offline diagnostic").assertDoesNotExist()
            if (failure is WorkOrderDetailOutcome.Failure.Network) {
                native.orders.detail = WorkOrderDetailOutcome.Success(native.order())
                compose.onNodeWithText(native.context.getString(R.string.provider_home_retry)).performClick()
                compose.waitUntil(10_000) { compose.onNodeWithText("Current authorized service detail").isDisplayed() }
                compose.onNodeWithText("Current authorized service detail").assertIsDisplayed()
            } else compose.onNodeWithText(native.context.getString(R.string.provider_home_retry)).assertDoesNotExist()
            Espresso.pressBack(); native.assertHome()
        }
        native.conversations.detailOutcome = ConversationDetailOutcome.Failure.Server(403, "private forbidden diagnostic")
        native.deliver(native.payload(event = "message:199:7")); native.tap()
        compose.waitUntil(10_000) { compose.onNodeWithText(native.context.getString(R.string.provider_conversation_error_forbidden)).isDisplayed() }
        compose.onNodeWithText(native.context.getString(R.string.provider_conversation_error_forbidden)).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_CHAT_INPUT_FIELD_TAG).assertDoesNotExist()
        compose.onNodeWithText("Ana Perez").assertDoesNotExist()
        Espresso.pressBack(); native.assertHome()
    }

    @Test fun expired_session_discards_native_tap_and_private_back_stack() {
        native.deliver(native.payload())
        val pending = native.waitNotice().notification.contentIntent
        compose.runOnIdle { native.sessions.clearSession() }
        native.tap(pending)
        compose.waitUntil(10_000) { compose.onNodeWithText(native.context.getString(R.string.welcome_login)).isDisplayed() }
        compose.onNodeWithText(native.context.getString(R.string.welcome_login)).assertIsDisplayed()
        compose.onNodeWithTag(PROVIDER_CONVERSATION_READY_TAG).assertDoesNotExist()
        assertTrue(native.notices().isEmpty())
        assertNull(native.store.read().binding?.takeIf { it.active })
    }
}
