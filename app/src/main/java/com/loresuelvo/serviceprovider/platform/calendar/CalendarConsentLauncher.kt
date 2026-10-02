package com.loresuelvo.serviceprovider.platform.calendar

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult

interface CalendarConsentLauncher {
    fun authorize(
        activity: Activity,
        onResolution: (IntentSender) -> Unit,
        onResult: (CalendarConsentResult) -> Unit,
    )
    fun result(activity: Activity, intent: Intent?): CalendarConsentResult
}
