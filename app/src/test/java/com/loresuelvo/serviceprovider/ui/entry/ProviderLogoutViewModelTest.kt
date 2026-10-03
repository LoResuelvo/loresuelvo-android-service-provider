package com.loresuelvo.serviceprovider.ui.entry

import androidx.lifecycle.SavedStateHandle
import com.loresuelvo.serviceprovider.bdd.auth.logout.LogoutWorld
import com.loresuelvo.serviceprovider.domain.auth.LogoutOutcome
import kotlinx.coroutines.CompletableDeferred
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ProviderLogoutViewModelTest {
    private val world = LogoutWorld()
    private val root get() = world.viewModel
    @After fun close() = world.close()

    @Test fun cancel_confirmation_preserves_session_without_side_effects() {
        world.openConfirmation()
        root.dismissLogout()
        assertFalse(root.logoutState.value.confirmationVisible)
        assertEquals(world.session, world.store.getSession())
        assertEquals(0, world.store.clearCalls)
        assertNull(root.logoutState.value.launchId)
    }

    @Test fun confirmation_clears_local_access_before_external_result_and_launch_is_claimed_once() {
        world.openConfirmation()
        root.confirmLogout()
        root.confirmLogout()
        root.retryLogout()
        assertEquals(ProviderEntryUiState.Welcome, root.uiState.value)
        assertNull(world.store.getSession())
        assertNull(world.store.persisted)
        assertEquals(1, world.store.clearCalls)
        val id = checkNotNull(root.logoutState.value.launchId)
        assertTrue(root.claimLogoutLaunch(id))
        assertFalse(root.claimLogoutLaunch(id))
        root.onLogoutResult(id, LogoutOutcome.Success)
        assertFalse(root.logoutState.value.processing)
        assertFalse(root.logoutState.value.externalLogoutPending)
    }

    @Test fun cancellation_and_provider_failure_keep_welcome_and_allow_successful_retry() {
        for (outcome in listOf(LogoutOutcome.Cancelled, LogoutOutcome.Failure.Provider(null))) {
            world.store.saveSession(world.session)
            world.drain()
            world.openConfirmation()
            root.confirmLogout()
            val id = checkNotNull(root.logoutState.value.launchId)
            root.claimLogoutLaunch(id)
            root.onLogoutResult(id, outcome)
            assertEquals(ProviderEntryUiState.Welcome, root.uiState.value)
            assertTrue(root.logoutState.value.externalLogoutPending)
            root.retryLogout()
            world.completeBrowser()
            assertFalse(root.logoutState.value.externalLogoutPending)
            assertNull(world.store.persisted)
        }
    }

    @Test fun failed_durable_removal_blocks_access_and_authentication_until_retry_succeeds() {
        world.store.removalFails = true
        world.openConfirmation()
        root.confirmLogout()
        world.drain()
        assertEquals(ProviderEntryUiState.Welcome, root.uiState.value)
        assertNull(world.store.getSession())
        assertEquals(world.session, world.store.persisted)
        assertTrue(root.logoutState.value.localRemovalPending)
        assertNull(root.logoutState.value.launchId)
        assertFalse(root.acceptsAuthentication(root.authenticationGeneration()))
        world.store.removalFails = false
        root.retryLogout()
        world.completeBrowser()
        assertNull(world.store.persisted)
        assertFalse(root.logoutState.value.localRemovalPending)
        assertFalse(root.logoutState.value.externalLogoutPending)
    }

    @Test fun restored_pending_logout_allows_retry_without_automatic_browser_launch() {
        world.openConfirmation()
        root.confirmLogout()
        val restored = world.createRoot(SavedStateHandle(mapOf("logout.external.pending" to true)))
        world.drain()
        assertEquals(ProviderEntryUiState.Welcome, restored.uiState.value)
        assertTrue(restored.logoutState.value.externalLogoutPending)
        assertNull(restored.logoutState.value.launchId)
        restored.retryLogout()
        assertNotNull(restored.logoutState.value.launchId)
    }

    @Test fun late_logout_callback_cannot_clear_new_session_even_before_first_logout_finishes() {
        world.openConfirmation()
        root.confirmLogout()
        val id = checkNotNull(root.logoutState.value.launchId)
        root.claimLogoutLaunch(id)
        val next = world.session.copy(accessToken = "new-synthetic-token")
        world.store.saveSession(next)
        world.drain()
        assertTrue(root.uiState.value is ProviderEntryUiState.Home)
        root.onLogoutResult(id, LogoutOutcome.Failure.Provider(null))
        assertEquals(next, world.store.getSession())
        assertTrue(root.uiState.value is ProviderEntryUiState.Home)
        assertFalse(root.logoutState.value.externalLogoutPending)
    }

    @Test fun logout_invalidates_old_authentication_callback_generation() {
        val generation = root.authenticationGeneration()
        world.openConfirmation()
        world.confirm()
        assertFalse(root.acceptsAuthentication(generation))
        assertTrue(root.acceptsAuthentication(root.authenticationGeneration()))
    }

    @Test fun logout_cancels_pending_entry_resolution_and_new_login_can_resolve() {
        val pending = CompletableDeferred<CurrentAccountOutcome>()
        world.accountOutcome = { pending.await() }
        root.refresh()
        world.scheduler.runCurrent()
        world.openConfirmation()
        root.confirmLogout()
        world.completeBrowser()
        world.accountOutcome = { CurrentAccountOutcome.Failure.NotFound }
        world.store.saveSession(world.session.copy(accessToken = "next-token"))
        world.drain()
        pending.complete(CurrentAccountOutcome.Success(com.loresuelvo.serviceprovider.domain.account.CurrentAccount.Consumer))
        world.drain()
        assertEquals(ProviderEntryUiState.CompleteProviderProfile, root.uiState.value)
    }
}
