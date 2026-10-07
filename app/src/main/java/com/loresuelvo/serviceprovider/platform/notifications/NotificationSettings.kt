package com.loresuelvo.serviceprovider.platform.notifications

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal fun notificationSettingsIntent(packageName: String): Intent =
    if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    else applicationSettingsIntent(packageName)

private fun applicationSettingsIntent(packageName: String) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

fun openNotificationSettings(activity: Activity) {
    val intents = listOf(notificationSettingsIntent(activity.packageName), applicationSettingsIntent(activity.packageName))
        .distinctBy { it.action }
    for (intent in intents) {
        try { activity.startActivity(intent); return }
        catch (_: ActivityNotFoundException) { /* Try the standard application settings fallback. */ }
        catch (_: SecurityException) { /* Optional system settings must not interrupt app use. */ }
    }
}
