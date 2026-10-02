package com.loresuelvo.serviceprovider.domain.calendar

interface CalendarConnectionRepository {
    suspend fun connect(serverAuthCode: String, session: com.loresuelvo.serviceprovider.domain.auth.AuthSession): ConnectCalendarOutcome
}

sealed interface ConnectCalendarOutcome {
    data object Submitted : ConnectCalendarOutcome
    data object Unauthorized : ConnectCalendarOutcome
    data object Rejected : ConnectCalendarOutcome
    data object Unavailable : ConnectCalendarOutcome
}

sealed interface CalendarConsentResult {
    // Deliberately not a data class: credentials must never appear in toString().
    class Authorized(val serverAuthCode: String) : CalendarConsentResult
    data object Cancelled : CalendarConsentResult
    data object Denied : CalendarConsentResult
    data object Failed : CalendarConsentResult
}
