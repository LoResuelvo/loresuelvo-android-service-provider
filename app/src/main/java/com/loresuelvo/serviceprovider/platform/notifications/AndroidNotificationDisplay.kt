package com.loresuelvo.serviceprovider.platform.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.loresuelvo.serviceprovider.MainActivity
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.notifications.NotificationDisplay
import com.loresuelvo.serviceprovider.domain.notifications.ProviderNotification
import com.loresuelvo.serviceprovider.domain.notifications.NotificationDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNotificationDisplay @Inject constructor(@ApplicationContext private val context: Context) : NotificationDisplay {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        manager.createNotificationChannels(listOf(
            NotificationChannel(MESSAGES_CHANNEL, context.getString(R.string.provider_notification_messages_channel), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(SERVICES_CHANNEL, context.getString(R.string.provider_notification_services_channel), NotificationManager.IMPORTANCE_DEFAULT),
        ))
    }

    override fun canPost(): Boolean = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    override fun post(notice: ProviderNotification, tapId: String): Boolean {
        createChannels()
        if (!canPost()) return false
        val channel = if (notice.destination is NotificationDestination.Conversation) MESSAGES_CHANNEL else SERVICES_CHANNEL
        val intent = Intent(context, MainActivity::class.java).apply {
            action = TAP_ACTION
            data = Uri.parse("loresuelvo-notification://tap/$tapId")
            putExtra(TAP_EXTRA, tapId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tap = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_provider_notification)
            .setContentTitle(notice.title).setContentText(notice.body)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_provider_notification).setContentTitle(notice.title).setContentText(notice.body).build())
            .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
            .setTimeoutAfter((notice.expiresAt - System.currentTimeMillis()).coerceAtLeast(1))
            .build()
        return try { manager.notify("${notice.bindingId}:${notice.eventId}", 0, notification); true }
        catch (_: SecurityException) { false }
    }

    override fun cancelAll() = manager.cancelAll()

    companion object {
        const val MESSAGES_CHANNEL = "messages"
        const val SERVICES_CHANNEL = "services"
        const val TAP_ACTION = "com.loresuelvo.serviceprovider.OPEN_NOTIFICATION"
        const val TAP_EXTRA = "notification-tap"
    }
}
