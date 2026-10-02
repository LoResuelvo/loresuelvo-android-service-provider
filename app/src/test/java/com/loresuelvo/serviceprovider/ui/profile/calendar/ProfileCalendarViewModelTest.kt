package com.loresuelvo.serviceprovider.ui.profile.calendar

import com.loresuelvo.serviceprovider.domain.account.CalendarConnectionStatus
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConsentResult
import com.loresuelvo.serviceprovider.domain.calendar.ConnectCalendarOutcome
import com.loresuelvo.serviceprovider.ui.profile.CalendarFeedback
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCalendarViewModelTest {
    @Test
    fun post_does_not_confirm_connected_until_get_finishes() = ProfileCalendarFixture().use { f ->
        f.open(); f.start()
        val response = CompletableDeferred<CurrentAccountOutcome>()
        f.accountResponse = { response.await() }
        f.result(CalendarConsentResult.Authorized("code"))
        assertEquals(1, f.postCalls)
        assertEquals(CalendarConnectionStatus.Disconnected, f.status())
        assertTrue(f.viewModel.calendarState.value.loading)
        response.complete(CurrentAccountOutcome.Success(f.provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Connected)))
        f.drain()
        assertEquals(CalendarConnectionStatus.Connected, f.status())
        assertFalse(f.viewModel.calendarState.value.loading)
    }

    @Test
    fun successful_post_with_disconnected_get_never_announces_success() = ProfileCalendarFixture().use { f ->
        f.open(); f.start(); f.result(CalendarConsentResult.Authorized("code"))
        assertEquals(CalendarConnectionStatus.Disconnected, f.status())
        assertEquals(1, f.postCalls)
        assertEquals(2, f.accountCalls)
        f.start()
        assertEquals(2, f.launches.size)
    }

    @Test
    fun failed_confirmation_retries_only_get_even_on_profile_resume() = ProfileCalendarFixture().use { f ->
        f.open(); f.start()
        f.accountResponse = { CurrentAccountOutcome.Failure.Server(503) }
        f.result(CalendarConsentResult.Authorized("code"))
        assertTrue(f.viewModel.calendarState.value.confirmationRetry)
        f.viewModel.authorizeCalendar(); f.drain()
        assertEquals(1, f.launches.size)
        f.accountResponse = { CurrentAccountOutcome.Success(f.provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Connected)) }
        f.viewModel.onProfileResumed(); f.drain()
        assertEquals(1, f.postCalls)
        assertEquals(3, f.accountCalls)
        assertEquals(CalendarConnectionStatus.Connected, f.status())
    }

    @Test
    fun unknown_confirmation_status_remains_retryable_without_reposting() = ProfileCalendarFixture().use { f ->
        f.open(); f.start()
        f.provider = f.provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Unavailable)
        f.result(CalendarConsentResult.Authorized("code"))
        assertEquals(CalendarFeedback.ConfirmationFailed, f.viewModel.calendarState.value.feedback)
        f.viewModel.retryCalendarConfirmation(); f.drain()
        assertEquals(1, f.postCalls)
        assertEquals(3, f.accountCalls)
    }

    @Test
    fun pending_post_blocks_second_launch_and_duplicate_code_callback() = ProfileCalendarFixture().use { f ->
        f.open(); val launch = f.start()
        val response = CompletableDeferred<ConnectCalendarOutcome>()
        f.postResponse = { response.await() }
        f.result(CalendarConsentResult.Authorized("code"))
        f.viewModel.authorizeCalendar()
        f.viewModel.onCalendarResult(launch.attemptId, CalendarConsentResult.Authorized("duplicate"))
        f.drain()
        assertTrue(f.viewModel.calendarState.value.loading)
        assertEquals(1, f.launches.size)
        assertEquals(1, f.postCalls)
        assertEquals("code", f.postedCode)
        response.complete(ConnectCalendarOutcome.Submitted); f.drain()
    }

    @Test
    fun obsolete_callback_is_discarded_after_session_change() = ProfileCalendarFixture().use { f ->
        f.open(); val launch = f.start()
        f.sessionStore.saveSession(AuthSession(User("other", "other@example.com"), "other-token"))
        f.viewModel.onCalendarResult(launch.attemptId, CalendarConsentResult.Authorized("obsolete"))
        f.drain()
        assertEquals(0, f.postCalls)
        assertEquals(ProviderProfileUiState.SessionExpired, f.viewModel.uiState.value)
    }

    @Test
    fun session_change_between_callback_and_coroutine_execution_cannot_post() = ProfileCalendarFixture().use { f ->
        f.open(); val launch = f.start()
        f.viewModel.onCalendarResult(launch.attemptId, CalendarConsentResult.Authorized("obsolete"))
        f.sessionStore.saveSession(AuthSession(User("other", "other@example.com"), "other-token"))
        f.drain()
        assertEquals(0, f.postCalls)
        assertEquals(ProviderProfileUiState.SessionExpired, f.viewModel.uiState.value)
    }

    @Test
    fun leaving_profile_invalidates_consent_and_ignores_late_callbacks() = ProfileCalendarFixture().use { f ->
        f.open(); val launch = f.start()
        f.viewModel.leaveProfile()
        f.viewModel.onCalendarResult(launch.attemptId, CalendarConsentResult.Authorized("obsolete"))
        f.drain()
        assertEquals(0, f.postCalls)
        assertFalse(f.viewModel.calendarState.value.loading)
    }

    @Test
    fun launch_requires_valid_profile_status_and_cannot_be_claimed_twice() = ProfileCalendarFixture().use { f ->
        f.viewModel.authorizeCalendar(); f.drain()
        assertTrue(f.launches.isEmpty())
        f.provider = f.provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Unavailable)
        f.open(); f.viewModel.authorizeCalendar(); f.drain()
        assertTrue(f.launches.isEmpty())
        f.provider = f.provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Disconnected)
        f.open(); val launch = f.start()
        assertFalse(f.viewModel.claimCalendarLaunch(launch.attemptId))
    }

    @Test
    fun blank_code_is_rejected_without_a_request_and_can_start_new_consent() = ProfileCalendarFixture().use { f ->
        f.open(); f.start(); f.result(CalendarConsentResult.Authorized(" "))
        assertEquals(0, f.postCalls)
        assertEquals(CalendarFeedback.CodeRejected, f.viewModel.calendarState.value.feedback)
        f.start()
        assertEquals(2, f.launches.size)
    }

    @Test
    fun late_post_result_cannot_restore_private_profile_after_logout() = ProfileCalendarFixture().use { f ->
        f.open(); f.start()
        val response = CompletableDeferred<ConnectCalendarOutcome>()
        f.postResponse = { response.await() }
        f.result(CalendarConsentResult.Authorized("code"))
        f.sessionStore.clearSession(); f.drain()
        response.complete(ConnectCalendarOutcome.Submitted); f.drain()
        assertEquals(ProviderProfileUiState.SessionExpired, f.viewModel.uiState.value)
        assertEquals(1, f.accountCalls)
    }
    @Test
    fun pending_payment_result_preserves_calendar_confirmation_and_finishes_loading() = ProfileCalendarFixture().use { f ->
        val payment = CompletableDeferred<com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome>()
        f.paymentResponse = { payment.await() }
        f.open(); f.start()
        f.provider = f.provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Connected)
        f.result(CalendarConsentResult.Authorized("code"))
        assertEquals(com.loresuelvo.serviceprovider.ui.profile.ProfilePaymentState.Loading,
            (f.viewModel.uiState.value as ProviderProfileUiState.Ready).payment)
        payment.complete(com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome.Success(
            com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatus(
                com.loresuelvo.serviceprovider.domain.paymentaccount.ConnectionStatus.CONNECTED)))
        f.drain()
        assertEquals(CalendarConnectionStatus.Connected, f.status())
        assertEquals(com.loresuelvo.serviceprovider.ui.profile.ProfilePaymentState.Connected,
            (f.viewModel.uiState.value as ProviderProfileUiState.Ready).payment)
    }

    @Test
    fun identity_and_calendar_consents_are_mutually_exclusive() = ProfileCalendarFixture().use { f ->
        f.provider = f.provider.copy(identityVerificationStatus = com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus.Unverified)
        f.open(); f.start()
        f.viewModel.verifyIdentity(); f.drain()
        assertEquals(0, f.identityCalls)
        f.result(CalendarConsentResult.Cancelled)
        val identity = CompletableDeferred<com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome>()
        f.identityResponse = { identity.await() }
        f.viewModel.verifyIdentity(); f.drain()
        assertEquals(1, f.identityCalls)
        f.viewModel.authorizeCalendar(); f.drain()
        assertEquals(1, f.launches.size)
        identity.complete(com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome.Failure.Network)
        f.drain()
        assertFalse(f.viewModel.identityState.value.loading)
        assertTrue(f.viewModel.uiState.value is ProviderProfileUiState.Ready)
    }

    @Test
    fun unauthorized_confirmation_clears_session_without_reposting() = ProfileCalendarFixture().use { f ->
        f.open(); f.start()
        f.accountResponse = { CurrentAccountOutcome.Failure.Unauthorized }
        f.result(CalendarConsentResult.Authorized("code"))
        assertEquals(ProviderProfileUiState.SessionExpired, f.viewModel.uiState.value)
        assertEquals(null, f.sessionStore.getSession())
        assertEquals(1, f.postCalls)
        assertFalse(f.viewModel.calendarState.value.loading)
    }

    @Test
    fun confirmation_retry_waits_for_identity_and_then_queries_without_reposting() = ProfileCalendarFixture().use { f ->
        f.provider = f.provider.copy(identityVerificationStatus = com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus.Unverified)
        f.open(); f.start()
        f.accountResponse = { CurrentAccountOutcome.Failure.Server(503) }
        f.result(CalendarConsentResult.Authorized("code"))
        assertTrue(f.viewModel.calendarState.value.confirmationRetry)
        val identity = CompletableDeferred<com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome>()
        f.identityResponse = { identity.await() }
        f.viewModel.verifyIdentity(); f.drain()
        assertTrue(f.viewModel.identityState.value.loading)
        f.viewModel.retryCalendarConfirmation(); f.drain()
        assertEquals(2, f.accountCalls)
        assertFalse(f.viewModel.calendarState.value.loading)
        assertTrue(f.viewModel.calendarState.value.confirmationRetry)
        assertTrue(f.viewModel.uiState.value is ProviderProfileUiState.Ready)
        f.accountResponse = { CurrentAccountOutcome.Success(f.provider) }
        identity.complete(com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome.AlreadyApproved)
        f.drain()
        assertEquals(3, f.accountCalls)
        assertFalse(f.viewModel.identityState.value.loading)
        f.provider = f.provider.copy(calendarConnectionStatus = CalendarConnectionStatus.Connected)
        f.viewModel.retryCalendarConfirmation(); f.drain()
        assertEquals(4, f.accountCalls)
        assertEquals(CalendarConnectionStatus.Connected, f.status())
        assertEquals(1, f.postCalls)
        assertFalse(f.viewModel.calendarState.value.confirmationRetry)
    }

}
