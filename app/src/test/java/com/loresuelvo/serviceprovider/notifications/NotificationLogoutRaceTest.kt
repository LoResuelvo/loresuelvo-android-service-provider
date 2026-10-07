package com.loresuelvo.serviceprovider.notifications

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryViewModel
import com.loresuelvo.serviceprovider.ui.entry.ProviderEntryUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationLogoutRaceTest {
    @Test fun replacement_before_queued_logout_preserves_new_binding_and_never_launches_old_browser_logout() {
        verifyReplacement(beforeExecution = true)
    }

    @Test fun replacement_during_suspended_delete_preserves_new_binding_and_never_launches_old_browser_logout() {
        verifyReplacement(beforeExecution = false)
    }

    private fun verifyReplacement(beforeExecution: Boolean) = NotificationFixture().use { world ->
        world.register()
        val originalSession = world.sessions.getSession()!!
        val account = object : CurrentAccountRepository {
            override suspend fun getCurrentAccount() = CurrentAccountOutcome.Success(CurrentAccount.Provider(7, "Provider", "Account",
                "provider@example.test", Category(1, "Service"), null))
        }
        val entry = ProviderEntryViewModel(world.sessions, ResolveProviderEntryUseCase(world.sessions, account), SavedStateHandle(), world.logoutUseCase, world.local)
        try {
            world.chat.scheduler.advanceUntilIdle()
            world.repository.removalGate = CompletableDeferred()
            entry.requestLogout()
            entry.confirmLogout()
            if (!beforeExecution) {
                world.chat.scheduler.runCurrent()
                assertEquals(1, world.repository.removed.size)
                assertEquals(originalSession, world.sessions.getSession())
            }
            val replacement = AuthSession(User("new-provider", "new@example.test"), "new-token")
            world.sessions.saveSession(replacement)
            world.chat.scope.launch { world.registerUseCase(8, "en") }
            world.chat.scheduler.runCurrent()
            val replacementBinding = world.store.read().binding
            assertEquals("new-provider", replacementBinding!!.subject)
            assertTrue(replacementBinding.active && replacementBinding.acknowledged)
            world.repository.removalGate!!.complete(Unit)
            world.chat.scheduler.advanceUntilIdle()
            assertEquals(replacement, world.sessions.getSession())
            assertEquals(replacementBinding, world.store.read().binding)
            assertFalse(world.local.isInvalidated())
            assertNull(entry.logoutState.value.launchId)
            assertFalse(entry.logoutState.value.externalLogoutPending)
            assertFalse(entry.logoutState.value.processing)
            assertTrue(entry.uiState.value is ProviderEntryUiState.Home)
            assertEquals(if (beforeExecution) 0 else 1, world.repository.removed.size)
        } finally {
            ViewModelStore().apply { put("entry", entry) }.clear()
        }
    }
}
