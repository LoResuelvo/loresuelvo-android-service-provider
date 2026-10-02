package com.loresuelvo.serviceprovider.acceptance.profile

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import com.loresuelvo.serviceprovider.acceptance.auth.ProviderSignupCurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.account.CalendarConnectionStatus
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConnectionRepository
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult
import com.loresuelvo.serviceprovider.domain.calendar.ConnectCalendarOutcome
import com.loresuelvo.serviceprovider.platform.calendar.CalendarConsentLauncher
import com.loresuelvo.serviceprovider.testing.ProfileCalendarConsentTestActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileCalendarTestRepository @Inject constructor(
    private val account: ProviderSignupCurrentAccountRepository,
) : CalendarConnectionRepository {
    var calls = 0
        private set
    override suspend fun connect(serverAuthCode: String, session: AuthSession): ConnectCalendarOutcome {
        check(serverAuthCode == "android-test-server-code")
        calls++
        val provider = (account.outcome as CurrentAccountOutcome.Success).account as CurrentAccount.Provider
        account.outcome = CurrentAccountOutcome.Success(provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Connected))
        return ConnectCalendarOutcome.Submitted
    }
}

@Singleton
class ProfileCalendarTestLauncher @Inject constructor() : CalendarConsentLauncher {
    var calls = 0
        private set
    var parsedResults = 0
        private set
    var resolveImmediately = true
    private val resolutions = mutableListOf<() -> Unit>()
    private val results = mutableListOf<(CalendarConsentResult) -> Unit>()
    override fun authorize(activity: Activity, onResolution: (IntentSender) -> Unit, onResult: (CalendarConsentResult) -> Unit) {
        calls++
        val sender = PendingIntent.getActivity(activity, calls,
            Intent(activity, ProfileCalendarConsentTestActivity::class.java),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE).intentSender
        resolutions += { onResolution(sender) }
        results += onResult
        if (resolveImmediately) resolutions.last().invoke()
    }
    fun resolve(index: Int = calls - 1) = resolutions[index].invoke()
    fun completeWithoutResolution(result: CalendarConsentResult, index: Int = calls - 1) = results[index].invoke(result)
    override fun result(activity: Activity, intent: Intent?): CalendarConsentResult {
        parsedResults++
        return if (intent?.getBooleanExtra("authorized", false) == true)
            CalendarConsentResult.Authorized("android-test-server-code") else CalendarConsentResult.Denied
    }
}
