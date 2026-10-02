package com.loresuelvo.serviceprovider.platform.calendar

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.loresuelvo.serviceprovider.BuildConfig
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult
import javax.inject.Inject

class GoogleCalendarConsentLauncher @Inject constructor() : CalendarConsentLauncher {
    override fun authorize(
        activity: Activity,
        onResolution: (IntentSender) -> Unit,
        onResult: (CalendarConsentResult) -> Unit,
    ) {
        if (BuildConfig.GOOGLE_CALENDAR_SERVER_CLIENT_ID.isBlank()) {
            onResult(CalendarConsentResult.Failed)
            return
        }
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(CALENDAR_SCOPE)))
                .requestOfflineAccess(BuildConfig.GOOGLE_CALENDAR_SERVER_CLIENT_ID)
                .build()
            Identity.getAuthorizationClient(activity).authorize(request)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        val sender = result.pendingIntent?.intentSender
                        if (sender == null) onResult(CalendarConsentResult.Failed)
                        else onResolution(sender)
                    } else onResult(result.toCalendarConsentResult())
                }
                .addOnFailureListener { onResult(it.toConsentFailure()) }
        } catch (error: Exception) {
            onResult(error.toConsentFailure())
        }
    }

    override fun result(activity: Activity, intent: Intent?): CalendarConsentResult = try {
        Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intent).toCalendarConsentResult()
    } catch (error: Exception) {
        error.toConsentFailure()
    }

    private fun Exception.toConsentFailure(): CalendarConsentResult = when ((this as? ApiException)?.statusCode) {
        CommonStatusCodes.CANCELED -> CalendarConsentResult.Cancelled
        else -> CalendarConsentResult.Failed
    }

}

private const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar.events.owned"

internal fun AuthorizationResult.toCalendarConsentResult(): CalendarConsentResult {
    if (!grantedScopes.contains(CALENDAR_SCOPE)) return CalendarConsentResult.Denied
    val code = serverAuthCode?.takeIf { it.isNotBlank() } ?: return CalendarConsentResult.Failed
    return CalendarConsentResult.Authorized(code)
}
