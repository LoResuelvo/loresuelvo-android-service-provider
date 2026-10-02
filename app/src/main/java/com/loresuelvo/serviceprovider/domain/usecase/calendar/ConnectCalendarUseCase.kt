package com.loresuelvo.serviceprovider.domain.usecase.calendar

import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConnectionRepository
import com.loresuelvo.serviceprovider.domain.calendar.ConnectCalendarOutcome
import javax.inject.Inject

class ConnectCalendarUseCase @Inject constructor(
    private val repository: CalendarConnectionRepository,
    private val sessionStore: AuthSessionStore,
) {
    suspend operator fun invoke(serverAuthCode: String, expectedSession: com.loresuelvo.serviceprovider.domain.auth.AuthSession): ConnectCalendarOutcome {
        val session = sessionStore.getSession() ?: return ConnectCalendarOutcome.Unauthorized
        if (session != expectedSession) return ConnectCalendarOutcome.Unauthorized
        if (serverAuthCode.isBlank()) return ConnectCalendarOutcome.Rejected
        val outcome = repository.connect(serverAuthCode, session)
        if (sessionStore.getSession() != session) return ConnectCalendarOutcome.Unauthorized
        if (outcome == ConnectCalendarOutcome.Unauthorized) sessionStore.clearSession()
        return outcome
    }
}
