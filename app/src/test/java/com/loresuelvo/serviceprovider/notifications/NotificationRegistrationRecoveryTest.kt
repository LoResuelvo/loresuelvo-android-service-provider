package com.loresuelvo.serviceprovider.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.notifications.*
import com.loresuelvo.serviceprovider.domain.usecase.notifications.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRegistrationRecoveryTest {
    @Test fun request_never_reached_and_lost_success_reconcile_after_restart_without_inventing_installation() {
        listOf(false, true).forEach { reached -> NotificationFixture().use { world ->
            val backend = InstallationBackend()
            backend.interruptNext = if (reached) Interruption.AfterApply else Interruption.BeforeApply
            var register = useCase(world, backend)
            runTest(world.chat.scheduler) { assertEquals(RegistrationOutcome.Unavailable, register(world.verifyProvider(), "es")) }
            val first = world.store.read()
            assertFalse(first.binding!!.acknowledged)
            assertEquals(listOf(first.binding.id), first.attemptedBindingIds)
            // Recreate the registration decision maker; persisted metadata is the only predecessor evidence.
            register = useCase(world, backend)
            replace(world, "second", 8)
            runTest(world.chat.scheduler) { assertEquals(RegistrationOutcome.Ready, register(world.verifyProvider(8), "en")) }
            assertEquals(first.id, world.store.read().id)
            assertEquals(first.secret, world.store.read().secret)
            assertEquals(world.store.read().binding!!.id, backend.binding)
            assertEquals("second", backend.owner)
            assertTrue(world.store.read().binding!!.acknowledged)
            assertTrue(backend.calls.any { it.previousBindingId == if (reached) first.binding.id else null })
        } }
    }

    @Test fun token_and_language_renew_use_same_binding_and_unchanged_foreground_does_not_repeat_put() = NotificationFixture().use { world ->
        val backend = InstallationBackend()
        val register = useCase(world, backend)
        runTest(world.chat.scheduler) {
            val account = world.verifyProvider()
            assertEquals(RegistrationOutcome.Ready, register(account, "es"))
            val binding = world.store.read().binding!!.id
            assertEquals(RegistrationOutcome.Ready, register(account, "es"))
            assertEquals(1, backend.calls.size)
            world.token = "renewed-token"
            assertEquals(RegistrationOutcome.Ready, register(account, "en"))
            assertEquals(binding, backend.binding)
            assertEquals("en", backend.locale)
            assertEquals("renewed-token", backend.token)
        }
    }

    @Test fun ordinary_rejections_are_terminal_and_failed_metadata_is_never_acknowledged() = NotificationFixture().use { world ->
        val backend = InstallationBackend().apply { response = InstallationResult.Forbidden }
        val register = useCase(world, backend)
        runTest(world.chat.scheduler) {
            val account = world.verifyProvider()
            repeat(2) { assertEquals(RegistrationOutcome.Rejected, register(account, "es")) }
            assertEquals(1, backend.calls.size)
            assertNull(world.store.read().acknowledgedBindingId)
            assertFalse(world.store.read().binding!!.acknowledged)
        }
    }

    @Test fun multiple_lost_acknowledgements_keep_all_possible_predecessors_and_stop_at_capacity() = NotificationFixture().use { world ->
        val backend = InstallationBackend()
        val register = useCase(world, backend)
        runTest(world.chat.scheduler) {
            repeat(RegisterNotificationInstallationUseCase.MAX_ATTEMPTED_BINDINGS) { index ->
                replace(world, "provider-$index", index + 7)
                backend.interruptNext = Interruption.AfterApply
                assertEquals(RegistrationOutcome.Unavailable, register(world.verifyProvider(index + 7), "es"))
            }
            val uncertain = world.store.read().attemptedBindingIds
            assertEquals(8, uncertain.size)
            replace(world, "capacity", 100)
            val calls = backend.calls.size
            assertEquals(RegistrationOutcome.CapacityReached, register(world.verifyProvider(100), "es"))
            assertEquals(uncertain, world.store.read().attemptedBindingIds)
            assertEquals(calls, backend.calls.size)
            assertFalse(world.store.read().binding!!.acknowledged)
        }
    }

    @Test fun stale_success_and_unauthorized_cannot_activate_or_clear_replacement_login() {
        listOf(InstallationResult.Applied, InstallationResult.Unauthorized).forEach { result -> NotificationFixture().use { world ->
            val backend = InstallationBackend().apply { gate = CompletableDeferred(); response = result }
            val register = useCase(world, backend)
            val old = world.verifyProvider()
            world.chat.scope.launch { register(old, "es") }
            world.chat.scheduler.runCurrent()
            replace(world, "replacement", 8)
            val replacement = world.sessions.getSession()
            backend.gate!!.complete(Unit)
            world.chat.scheduler.advanceUntilIdle()
            assertEquals(replacement, world.sessions.getSession())
            assertFalse(world.store.read().binding!!.active)
            assertFalse(world.store.read().binding!!.acknowledged)
            backend.gate = null; backend.response = null
            runTest(world.chat.scheduler) { assertEquals(RegistrationOutcome.Ready, register(world.verifyProvider(8), "es")) }
            assertEquals("replacement", backend.owner)
        } }
    }

    @Test fun stale_verified_provider_and_token_callback_cannot_register_new_login_with_old_numeric_id() = NotificationFixture().use { world ->
        val backend = InstallationBackend()
        val register = useCase(world, backend)
        val old = world.verifyProvider()
        replace(world, "replacement", 8)
        runTest(world.chat.scheduler) { assertEquals(RegistrationOutcome.Unauthenticated, register(old, "es")) }
        assertTrue(backend.calls.isEmpty())
        assertNull(world.local.accountFor(world.sessions.getSession()))
    }

    @Test fun foreground_recovery_is_async_uses_verified_provider_and_preserves_ordinary_conversation_use() = NotificationFixture().use { world ->
        val accountRepository = object : com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository {
            override suspend fun getCurrentAccount() = com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome.Success(
                com.loresuelvo.serviceprovider.domain.account.CurrentAccount.Provider(7, "Provider", "Account", "p@example.test",
                    com.loresuelvo.serviceprovider.domain.category.Category(1, "Service"), null))
        }
        runTest(world.chat.scheduler) {
            com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase(world.sessions, accountRepository, world.local)()
        }
        val model = com.loresuelvo.serviceprovider.ui.notifications.ProviderNotificationViewModel(
            world.registerUseCase, world.permissionUseCase, world.consumeUseCase, world.local, world.sessions)
        try {
            world.repository.result = InstallationResult.TransientFailure
            model.enter(7, "es"); world.chat.scheduler.advanceUntilIdle()
            assertFalse(world.store.read().binding!!.acknowledged)
            world.chat.open()
            world.repository.registrationGate = CompletableDeferred()
            world.repository.result = InstallationResult.Applied
            model.enter(7, "es"); world.chat.scheduler.runCurrent()
            assertEquals(42, world.chat.ready().detail.id)
            assertNotNull(world.sessions.getSession())
            assertFalse(world.store.read().binding!!.acknowledged)
            world.repository.registrationGate!!.complete(Unit); world.chat.scheduler.advanceUntilIdle()
            assertTrue(world.store.read().binding!!.acknowledged)
            val binding = world.store.read().binding!!.id
            model.enter(7, "en"); world.chat.scheduler.advanceUntilIdle()
            assertEquals(binding, world.store.read().binding!!.id)
            assertEquals("en", world.store.read().registrationLocale)
        } finally { androidx.lifecycle.ViewModelStore().apply { put("notifications", model) }.clear() }
    }

    @Test fun rapid_account_switches_skip_queued_intermediate_login_and_offline_logout_reconciles_last_server_binding() = NotificationFixture().use { world ->
        val backend = InstallationBackend()
        val register = useCase(world, backend)
        runTest(world.chat.scheduler) { register(world.verifyProvider(), "es") }
        val oldNotice = world.notice()
        val oldBinding = world.store.read().binding!!.id
        world.repository.result = InstallationResult.TransientFailure
        world.logout()
        assertNull(world.sessions.getSession())
        replace(world, "intermediate", 8)
        val intermediate = world.verifyProvider(8)
        world.chat.scope.launch { register(intermediate, "es") }
        replace(world, "current", 9)
        val current = world.verifyProvider(9)
        world.chat.scope.launch { register(current, "es") }
        world.chat.scheduler.advanceUntilIdle()
        assertEquals(2, backend.calls.size)
        assertEquals(oldBinding, backend.calls.last().previousBindingId)
        assertEquals("current", backend.owner)
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(oldNotice))
        assertEquals(ReceiptOutcome.Posted, world.notifications.receive(world.notice("message:124:9").copy(recipientId = 9)))
    }

    @Test fun stale_delete_after_rebinding_cannot_revoke_new_owner_and_current_session_restart_metadata_is_usable() = NotificationFixture().use { world ->
        val backend = InstallationBackend()
        val register = useCase(world, backend)
        val originalSession = world.sessions.getSession()!!
        runTest(world.chat.scheduler) { register(world.verifyProvider(), "es") }
        val original = world.store.read()
        replace(world, "replacement", 8)
        runTest(world.chat.scheduler) {
            register(world.verifyProvider(8), "es")
            backend.remove(original, originalSession)
        }
        assertFalse(backend.revoked)
        val recreatedLocal = NotificationLocalSession(world.store, world.display, world.conversations)
        assertEquals(world.verifyProvider(8), recreatedLocal.accountFor(world.sessions.getSession()))
        world.sessions.saveSession(AuthSession(User("replacement", "8@example.test"), "different-login-token"))
        assertNull(recreatedLocal.accountFor(world.sessions.getSession()))
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(world.notice("message:125:8").copy(recipientId = 8)))
    }

    @Test fun confirmed_recovery_resolves_uncertainty_across_more_than_capacity_successful_cycles() = NotificationFixture().use { world ->
        val backend = InstallationBackend()
        val register = useCase(world, backend)
        runTest(world.chat.scheduler) {
            repeat(12) { index ->
                replace(world, "cycle-$index", index + 7)
                val account = world.verifyProvider(index + 7)
                backend.interruptNext = Interruption.AfterApply
                assertEquals(RegistrationOutcome.Unavailable, register(account, "es"))
                assertEquals(RegistrationOutcome.Ready, register(account, "es"))
                assertTrue(world.store.read().attemptedBindingIds.isEmpty())
                world.token = "renewed-$index"
                assertEquals(RegistrationOutcome.Ready, register(account, "en"))
                assertTrue(world.store.read().attemptedBindingIds.isEmpty())
            }
        }
    }

    @Test fun account_replacement_winning_storage_boundary_prevents_old_binding_creation_or_http() = NotificationFixture().use { world ->
        val old = world.verifyProvider()
        val replacement = AuthSession(User("replacement", "r@example.test"), "replacement-token")
        world.store.onRead = {
            world.store.onRead = null
            world.sessions.saveSession(replacement)
        }
        runTest(world.chat.scheduler) { assertEquals(RegistrationOutcome.Unauthenticated, world.registerUseCase(old, "es")) }
        assertEquals(replacement, world.sessions.getSession())
        assertTrue(world.local.isInvalidated())
        assertNull(world.store.read().binding)
        assertTrue(world.repository.registered.isEmpty())
        assertNull(world.local.accountFor(replacement))
    }

    @Test fun delayed_me_and_queued_registration_cannot_revive_confirmed_logout_during_delete() = NotificationFixture().use { world ->
        world.register()
        val account = world.verifyProvider()
        val response = CompletableDeferred<Unit>()
        val repository = object : com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository {
            override suspend fun getCurrentAccount(): com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome {
                response.await()
                return com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome.Success(
                    com.loresuelvo.serviceprovider.domain.account.CurrentAccount.Provider(7, "Provider", "Account", "p@example.test",
                        com.loresuelvo.serviceprovider.domain.category.Category(1, "Service"), null))
            }
        }
        val resolve = com.loresuelvo.serviceprovider.domain.usecase.account.ResolveProviderEntryUseCase(world.sessions, repository, world.local)
        world.chat.scope.launch { resolve() }
        world.chat.scheduler.runCurrent()
        world.repository.removalGate = CompletableDeferred()
        world.chat.scope.launch { world.notifications.logout() }
        world.chat.scheduler.runCurrent()
        assertNotNull(world.sessions.getSession()) // DELETE still owns the short-lived originating session.
        response.complete(Unit)
        world.chat.scope.launch { world.registerUseCase(account, "es") }
        world.chat.scheduler.runCurrent()
        assertNull(world.local.accountFor(world.sessions.getSession()))
        assertFalse(world.store.read().binding!!.active)
        assertEquals(1, world.repository.registered.size)
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(world.notice()))
        world.repository.removalGate!!.complete(Unit)
        world.chat.scheduler.advanceUntilIdle()
        assertNull(world.sessions.getSession())
    }

    @Test fun same_jwt_explicit_login_can_verify_and_rebind_after_logout_without_accepting_old_tap() = NotificationFixture().use { world ->
        world.register()
        val originalSession = world.sessions.getSession()!!
        val originalBinding = world.store.read().binding!!.id
        val oldNotice = world.notice(); world.notifications.receive(oldNotice)
        val tap = world.display.notices.single().second
        world.logout()
        world.sessions.saveSession(originalSession)
        world.register()
        assertTrue(world.store.read().binding!!.active && world.store.read().binding!!.acknowledged)
        assertNotEquals(originalBinding, world.store.read().binding!!.id)
        world.notifications.acceptTap(tap); assertNull(world.notifications.consumeTarget())
        assertEquals(ReceiptOutcome.Rejected, world.notifications.receive(oldNotice))
    }

    @Test fun reordered_same_account_token_callbacks_and_foreground_always_read_current_serialized_token() = NotificationFixture().use { world ->
        world.register()
        val account = world.verifyProvider()
        world.token = "latest-sdk-token"
        // Both callbacks capture only the account; a delayed older callback has no token argument to overwrite it.
        world.chat.scope.launch { world.registerUseCase(account, "es") }
        world.chat.scope.launch { world.registerUseCase(account, "es") }
        world.chat.scope.launch { world.registerUseCase(account, "en") }
        world.chat.scheduler.advanceUntilIdle()
        assertEquals("latest-sdk-token", world.store.read().registrationToken)
        assertEquals("en", world.store.read().registrationLocale)
        assertEquals(3, world.repository.registered.size) // initial, renewed token, changed locale.
    }

    private fun useCase(world: NotificationFixture, backend: InstallationBackend) = RegisterNotificationInstallationUseCase(
        world.sessions, world.store, backend, object : NotificationTokenSource { override suspend fun token() = world.token }, world.local)
    private fun replace(world: NotificationFixture, subject: String, recipient: Int) {
        world.local.invalidate()
        world.sessions.saveSession(AuthSession(User(subject, "$recipient@example.test"), "token-$subject"))
    }
}

/** Deterministic model of the reviewed API predecessor/owner contract, not Firebase delivery. */
class InstallationBackend : InstallationRepository {
    val calls = mutableListOf<NotificationInstallation>()
    var binding: String? = null
    var owner: String? = null
    var token: String? = null
    var locale: String? = null
    var revoked = false
    var interruptNext: Interruption? = null
    var response: InstallationResult? = null
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun register(installation: NotificationInstallation, token: String, locale: String, session: AuthSession): InstallationResult {
        calls += installation
        gate?.await()
        response?.let { if (it != InstallationResult.Applied) return it }
        val interruption = interruptNext
        if (interruption == Interruption.BeforeApply) { interruptNext = null; return InstallationResult.TransientFailure }
        val requested = checkNotNull(installation.binding).id
        val valid = if (binding == null) installation.previousBindingId == null
            else if (binding == requested) owner == session.user.id && !revoked
            else installation.previousBindingId == binding
        if (!valid) return InstallationResult.Conflict
        binding = requested; owner = session.user.id; this.token = token; this.locale = locale; revoked = false
        if (interruption == Interruption.AfterApply) { interruptNext = null; return InstallationResult.TransientFailure }
        return InstallationResult.Applied
    }
    override suspend fun remove(installation: NotificationInstallation, session: AuthSession): InstallationResult {
        if (installation.binding?.id == binding && owner == session.user.id) revoked = true
        return InstallationResult.Applied
    }
}
enum class Interruption { BeforeApply, AfterApply }
