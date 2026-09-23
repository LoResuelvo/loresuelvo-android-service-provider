package com.loresuelvo.serviceprovider.ui.profile.identity

import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.IdentityVerificationStatus
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.identity.IdentityVerificationResult
import com.loresuelvo.serviceprovider.domain.identity.StartIdentityVerificationOutcome
import com.loresuelvo.serviceprovider.domain.paymentaccount.PaymentAccountStatusOutcome
import com.loresuelvo.serviceprovider.ui.identity.IdentityVerificationFeedback
import com.loresuelvo.serviceprovider.ui.profile.ProviderProfileUiState
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test

class ProfileIdentityViewModelTest {
    @Test
    fun `duplicate taps and launch delivery start only one attempt`() = ProfileIdentityFixture().use { f ->
        f.open()
        val launch = f.start()
        f.viewModel.verifyIdentity()
        f.drain()
        assertEquals(1, f.startCalls)
        assertEquals(1, f.launches.size)
        assertFalse(f.viewModel.claimIdentityLaunch(launch.attemptId))
        assertTrue(f.viewModel.identityState.value.loading)
    }

    @Test
    fun `blocked and unavailable profiles cannot request a session`() = ProfileIdentityFixture().use { f ->
        listOf(IdentityVerificationStatus.Approved, IdentityVerificationStatus.InProgress,
            IdentityVerificationStatus.InReview, IdentityVerificationStatus.Resubmitted,
            IdentityVerificationStatus.Unavailable).forEach { status ->
            f.provider = f.provider.copy(identityVerificationStatus = status)
            f.open()
            f.viewModel.verifyIdentity()
            f.drain()
        }
        f.accountResponse = { CurrentAccountOutcome.Failure.Invalid }
        f.viewModel.refresh()
        f.viewModel.verifyIdentity()
        f.drain()
        f.viewModel.verifyIdentity()
        assertEquals(0, f.startCalls)
    }

    @Test
    fun `SDK callback and resume refresh once in either order`() {
        listOf(true, false).forEach { callbackFirst ->
            ProfileIdentityFixture().use { f ->
                f.open()
                val launch = f.start()
                f.viewModel.onProfilePaused()
                f.provider = f.provider.copy(identityVerificationStatus = IdentityVerificationStatus.InProgress)
                if (!callbackFirst) f.viewModel.onProfileResumed()
                f.viewModel.onIdentityResult(launch.attemptId, IdentityVerificationResult.Failed)
                f.drain()
                if (callbackFirst) {
                    assertEquals(1, f.accountCalls)
                    f.viewModel.onProfileResumed()
                }
                f.drain()
                assertEquals(2, f.accountCalls)
                assertEquals(IdentityVerificationStatus.InProgress, (f.viewModel.uiState.value as ProviderProfileUiState.Ready).provider.identityVerificationStatus)
                assertEquals(IdentityVerificationFeedback.Failed, f.viewModel.identityState.value.feedback)
                f.viewModel.verifyIdentity()
                assertEquals(1, f.startCalls)
                f.viewModel.onIdentityResult(launch.attemptId, IdentityVerificationResult.Completed)
                f.drain()
                assertEquals(2, f.accountCalls)
                f.viewModel.onProfilePaused()
                f.viewModel.onProfileResumed()
                f.drain()
                assertEquals(3, f.accountCalls)
            }
        }
    }

    @Test
    fun `start failures and conflict refresh without opening SDK`() {
        listOf(StartIdentityVerificationOutcome.Failure.Network,
            StartIdentityVerificationOutcome.Failure.InvalidResponse,
            StartIdentityVerificationOutcome.Failure.Forbidden,
            StartIdentityVerificationOutcome.Failure.Server(503),
            StartIdentityVerificationOutcome.Failure.Unknown,
            StartIdentityVerificationOutcome.AlreadyApproved).forEach { outcome ->
            ProfileIdentityFixture().use { f ->
                f.open()
                f.startResponse = { outcome }
                f.provider = f.provider.copy(identityVerificationStatus = IdentityVerificationStatus.Approved)
                f.viewModel.verifyIdentity()
                f.drain()
                assertEquals(2, f.accountCalls)
                assertTrue(f.launches.isEmpty())
                assertNull((f.viewModel.uiState.value as ProviderProfileUiState.Ready).provider.identityVerificationStatus.availableAction)
                assertFalse(f.viewModel.identityState.value.loading)
            }
        }
    }

    @Test
    fun `failed refresh locks identity until explicit recovery`() = ProfileIdentityFixture().use { f ->
        f.open()
        val launch = f.start()
        f.accountResponse = { CurrentAccountOutcome.Failure.Invalid }
        f.viewModel.onIdentityResult(launch.attemptId, IdentityVerificationResult.PermissionDenied)
        f.drain()
        assertEquals(ProviderProfileUiState.Unavailable, f.viewModel.uiState.value)
        f.viewModel.verifyIdentity()
        assertEquals(1, f.startCalls)
        f.accountResponse = { CurrentAccountOutcome.Success(f.provider) }
        f.viewModel.refresh()
        f.drain()
        f.start()
        assertEquals(2, f.startCalls)
        f.viewModel.onIdentityResult(launch.attemptId, IdentityVerificationResult.Completed)
        assertTrue(f.viewModel.identityState.value.loading)
    }

    @Test
    fun `pending payment cannot block identity return refresh`() = ProfileIdentityFixture().use { f ->
        val pending = CompletableDeferred<PaymentAccountStatusOutcome>()
        f.paymentResponse = { pending.await() }
        f.open()
        val launch = f.start()
        f.viewModel.onIdentityResult(launch.attemptId, IdentityVerificationResult.Cancelled)
        f.drain()
        assertEquals(2, f.accountCalls)
        assertFalse(f.viewModel.identityState.value.loading)
        assertEquals(IdentityVerificationFeedback.Cancelled, f.viewModel.identityState.value.feedback)
    }

    @Test
    fun `session change or route exit invalidates a pending launch`() {
        listOf(true, false).forEach { changeSession ->
            ProfileIdentityFixture().use { f ->
                f.open()
                f.viewModel.verifyIdentity()
                f.drain()
                val launch = f.launches.single()
                if (changeSession) f.sessionStore.saveSession(AuthSession(User("new", "new@example.com"), "new-token"))
                else f.viewModel.leaveProfile()
                assertFalse(f.viewModel.claimIdentityLaunch(launch.attemptId))
            }
        }
    }

    @Test
    fun `unauthorized start expires session without retry or launch`() = ProfileIdentityFixture().use { f ->
        f.open()
        f.startResponse = { StartIdentityVerificationOutcome.Failure.Unauthorized }
        f.viewModel.verifyIdentity()
        f.drain()
        assertNull(f.sessionStore.getSession())
        assertEquals(ProviderProfileUiState.SessionExpired, f.viewModel.uiState.value)
        assertTrue(f.launches.isEmpty())
        assertEquals(1, f.accountCalls)
    }
}
