package com.loresuelvo.serviceprovider.platform.notifications

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.loresuelvo.serviceprovider.domain.notifications.NotificationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseNotificationTokenSource @Inject constructor(@ApplicationContext private val context: Context) : NotificationTokenSource {
    override suspend fun token(): String? {
        if (FirebaseApp.getApps(context).isEmpty() ||
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return null
        return try {
            suspendCancellableCoroutine { continuation ->
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (continuation.isActive) continuation.resume(if (task.isSuccessful) task.result?.takeIf { it.isNotBlank() } else null)
                }
            }
        } catch (_: IllegalStateException) { null }
    }
}
