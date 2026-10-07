package com.loresuelvo.serviceprovider.notifications

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import com.loresuelvo.serviceprovider.platform.notifications.notificationSettingsIntent
import com.loresuelvo.serviceprovider.platform.notifications.openNotificationSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationSettingsTest {
    @Test @Config(sdk = [24, 25]) fun pre_channel_android_uses_application_settings() {
        val intent = notificationSettingsIntent("provider.test")
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:provider.test", intent.dataString)
    }
    @Test @Config(sdk = [34]) fun current_android_opens_notification_settings_for_this_package() {
        val intent = notificationSettingsIntent("provider.test")
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals("provider.test", intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }
    @Test @Config(sdk = [34]) fun missing_notification_settings_falls_back_without_crashing() {
        val intents = mutableListOf<Intent>()
        val activity = object : Activity() {
            override fun getPackageName() = "provider.test"
            override fun startActivity(intent: Intent) {
                intents += intent
                if (intent.action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) throw ActivityNotFoundException()
            }
        }
        openNotificationSettings(activity)
        assertEquals(listOf(Settings.ACTION_APP_NOTIFICATION_SETTINGS, Settings.ACTION_APPLICATION_DETAILS_SETTINGS), intents.map { it.action })
    }
}
