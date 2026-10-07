package com.loresuelvo.serviceprovider.acceptance.notifications

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.*
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.platform.notifications.AndroidNotificationDisplay
import com.loresuelvo.serviceprovider.ui.screens.profile.PROVIDER_PROFILE_DATA_TAG
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.allOf
import org.junit.*
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NativeNotificationPermissionTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private lateinit var native: NativeNotificationHarness
    private var accessibilityFlags = 0
    @Before fun setup() {
        hilt.inject(); native = NativeNotificationHarness(compose)
        val automation = native.instrumentation.uiAutomation
        val info = automation.serviceInfo; accessibilityFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        automation.serviceInfo = info
    }
    @After fun teardown() {
        if (::native.isInitialized) {
            val automation = native.instrumentation.uiAutomation
            val info = automation.serviceInfo; info.flags = accessibilityFlags; automation.serviceInfo = info
            native.close()
        }
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    fun provider_entry_requests_real_permission_once_and_grant_allows_service_receipt() {
        resetPrompt()
        native.account.outcome = CurrentAccountOutcome.Failure.NotFound
        native.launch()
        assertFalse(native.store.read().permissionRequested)
        assertNull(permissionButton("permission_allow_button"))
        native.closeActivity()
        native.setProvider(7); native.launch()
        clickPermission("permission_allow_button")
        compose.waitUntil(10_000) { native.hasPermission() }
        native.waitRegistered()
        assertTrue(native.store.read().permissionRequested)
        native.deliver(native.payload()); native.waitNotice()
        native.closeActivity(); native.launch(); native.assertHome()
        assertNull(permissionButton("permission_allow_button"))
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    fun denial_keeps_messages_services_and_profile_system_settings_usable_without_reprompting() {
        resetPrompt(); native.launch()
        clickPermission("permission_deny_button")
        compose.waitUntil(10_000) { permissionButton("permission_deny_button") == null }
        assertFalse(native.hasPermission())
        native.assertHome(); native.waitRegistered()
        native.deliver(native.payload()); assertTrue(native.notices().isEmpty())
        compose.onNodeWithTag(com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX + com.loresuelvo.serviceprovider.ui.navigation.Route.Messages.path).performClick()
        compose.onNodeWithText("Ana Perez").assertIsDisplayed()
        compose.onNodeWithTag(com.loresuelvo.serviceprovider.ui.components.bottomnav.PROVIDER_BOTTOM_BAR_ITEM_PREFIX + com.loresuelvo.serviceprovider.ui.navigation.Route.ProviderTurns.path).performClick()
        compose.onNodeWithText(native.context.getString(R.string.provider_turns_title)).assertIsDisplayed()
        native.closeActivity(); native.launch(); native.assertHome()
        assertTrue(native.store.read().permissionRequested)
        assertNull(permissionButton("permission_allow_button"))
        assertNull(permissionButton("permission_deny_button"))
        native.openProfile()
        Intents.init()
        try {
            compose.onNodeWithTag("provider-notification-settings").performScrollTo().performClick()
            Intents.intended(allOf(hasAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS), hasExtra(Settings.EXTRA_APP_PACKAGE, native.context.packageName)))
            compose.waitUntil(10_000) { native.instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()?.contains("settings") == true }
            backFromSettings()
            native.awaitActivity()
            compose.onNodeWithTag(PROVIDER_PROFILE_DATA_TAG).assertIsDisplayed()
        } finally { Intents.release() }
    }

    @Test @SdkSuppress(minSdkVersion = 26)
    fun operating_system_channel_disable_prevents_message_alerts_and_restores_previous_channel_state() {
        native.launch(); native.waitRegistered()
        openChannel()
        val initial = channelSwitch().isChecked
        assertTrue("The native message channel must start enabled for this delivery check", initial)
        try {
            assertTrue(channelSwitch().performAction(AccessibilityNodeInfo.ACTION_CLICK))
            compose.waitUntil(10_000) { native.manager.getNotificationChannel(AndroidNotificationDisplay.MESSAGES_CHANNEL).importance == NotificationManager.IMPORTANCE_NONE }
            backFromSettings()
            native.awaitActivity()
            native.deliver(native.payload()); assertTrue(native.notices().isEmpty())
            native.deliver(native.payload(NativeNotificationHarness.ACCEPTED, 55))
            native.waitNotice() // Independently enabled services channel remains usable.
        } finally {
            openChannel()
            if (channelSwitch().isChecked != initial) assertTrue(channelSwitch().performAction(AccessibilityNodeInfo.ACTION_CLICK))
            compose.waitUntil(10_000) { native.manager.getNotificationChannel(AndroidNotificationDisplay.MESSAGES_CHANNEL).importance != NotificationManager.IMPORTANCE_NONE }
            backFromSettings()
            native.awaitActivity()
        }
    }

    private fun backFromSettings() {
        val automation = native.instrumentation.uiAutomation
        assertTrue(automation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK), true))
        assertTrue(automation.injectInputEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK), true))
    }
    private fun resetPrompt() { native.revokePermission(); native.store.write(native.store.read().copy(permissionRequested = false)) }
    private fun permissionButton(id: String) = nodes(native.instrumentation.uiAutomation.rootInActiveWindow)
        .firstOrNull { it.viewIdResourceName?.endsWith(":id/$id") == true }
    private fun clickPermission(id: String) {
        compose.waitUntil(10_000) { permissionButton(id) != null }
        assertTrue(checkNotNull(permissionButton(id)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun openChannel() {
        native.context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, native.context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidNotificationDisplay.MESSAGES_CHANNEL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(10_000) { nodes(native.instrumentation.uiAutomation.rootInActiveWindow).any(::isSwitch) }
    }
    private fun channelSwitch() = nodes(native.instrumentation.uiAutomation.rootInActiveWindow).first(::isSwitch)
    private fun isSwitch(node: AccessibilityNodeInfo) = node.isCheckable && node.isEnabled && node.className?.toString()?.contains("Switch") == true
    private fun nodes(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> = if (root == null) emptyList()
        else listOf(root) + (0 until root.childCount).flatMap { nodes(root.getChild(it)) }
}
