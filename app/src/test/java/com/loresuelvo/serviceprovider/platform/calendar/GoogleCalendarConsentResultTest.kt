package com.loresuelvo.serviceprovider.platform.calendar

import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GoogleCalendarConsentResultTest {
    @Test
    fun only_granted_owned_calendar_scope_and_nonblank_server_code_authorize() {
        val result = mockk<AuthorizationResult>()
        every { result.grantedScopes } returns emptyList()
        assertEquals(CalendarConsentResult.Denied, result.toCalendarConsentResult())
        every { result.grantedScopes } returns listOf("https://www.googleapis.com/auth/calendar.events.owned")
        for (code in listOf(null, "", " ")) {
            every { result.serverAuthCode } returns code
            assertEquals(CalendarConsentResult.Failed, result.toCalendarConsentResult())
        }
        every { result.serverAuthCode } returns "server-code"
        val authorized = result.toCalendarConsentResult() as CalendarConsentResult.Authorized
        assertEquals("server-code", authorized.serverAuthCode)
        assertFalse(authorized.toString().contains("server-code"))
        verify(exactly = 0) { result.accessToken }
    }
}
