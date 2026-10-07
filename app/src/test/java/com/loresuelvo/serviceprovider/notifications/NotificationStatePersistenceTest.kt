package com.loresuelvo.serviceprovider.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.data.notifications.EncryptedNotificationStateStore
import com.loresuelvo.serviceprovider.domain.notifications.HandledNotification
import com.loresuelvo.serviceprovider.domain.notifications.NotificationBinding
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotificationStatePersistenceTest {
    @Test fun process_recreated_storage_retains_installation_binding_handled_ids_and_permission_denial() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("push-persistence", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val first = EncryptedNotificationStateStore(preferences)
        val installation = first.read().copy(binding = NotificationBinding("binding", "auth0-subject", 7, true, true),
            previousBindingId = "previous", permissionRequested = true, acknowledgedBindingId = "binding",
            attemptedBindingIds = listOf("uncertain"), registrationToken = "synthetic-token", registrationLocale = "es",
            handled = listOf(HandledNotification("message:123:7", "binding", com.loresuelvo.serviceprovider.domain.notifications.NotificationDestination.Conversation(42), 60_000, "capability")))
        assertTrue(first.write(installation))
        val recreated = EncryptedNotificationStateStore(preferences)
        assertEquals(installation, recreated.read())
        assertTrue(recreated.write(recreated.read().copy(binding = installation.binding!!.copy(active = false), handled = emptyList())))
        assertFalse(EncryptedNotificationStateStore(preferences).read().binding!!.active)
        assertTrue(EncryptedNotificationStateStore(preferences).read().handled.isEmpty())
    }

    @Test fun old_untyped_capabilities_are_dropped_but_acknowledged_predecessor_and_installation_survive_migration() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("push-migration", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("state", """{"id":"installation","secret":"secret","previous":"known-server-binding",
            "binding":{"id":"failed-new-binding","subject":"provider","recipient":7,"active":false,"acknowledged":false},
            "handled":[{"event":"message:123:7","binding":"failed-new-binding","conversation":42,"expires":60000,"tap":"old-capability"}]}""").commit()
        val state = EncryptedNotificationStateStore(preferences).read()
        assertEquals("installation", state.id); assertEquals("secret", state.secret)
        assertEquals("known-server-binding", state.acknowledgedBindingId)
        assertNull(state.binding!!.sessionKey)
        assertTrue(state.handled.isEmpty())
    }

    @Test fun shared_auth_establishment_allows_same_jwt_relogin_but_inactive_proof_stays_rejected_after_restart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("push-establishment", Context.MODE_PRIVATE)
        val authPreferences = context.getSharedPreferences("push-auth-establishment", Context.MODE_PRIVATE)
        preferences.edit().clear().commit(); authPreferences.edit().clear().commit()
        val store = EncryptedNotificationStateStore(preferences)
        val local = com.loresuelvo.serviceprovider.domain.notifications.NotificationLocalSession(store, RecordingNotificationDisplay(),
            com.loresuelvo.serviceprovider.domain.notifications.NotificationConversationState())
        val sessions = com.loresuelvo.serviceprovider.data.auth.EncryptedAuthSessionStore(authPreferences, local)
        val session = com.loresuelvo.serviceprovider.domain.auth.AuthSession(
            com.loresuelvo.serviceprovider.domain.auth.User("provider", "p@example.test"), "same-jwt")
        sessions.saveSession(session)
        val key = com.loresuelvo.serviceprovider.domain.notifications.VerifiedNotificationAccount.from(session, 7).sessionKey
        store.write(store.read().copy(binding = NotificationBinding("old", "provider", 7, true, true, key)))
        val previousGeneration = local.generation()
        local.invalidate()
        assertFalse(local.verify(session, 7, previousGeneration))
        val recreated = EncryptedNotificationStateStore(preferences)
        val recreatedLocal = com.loresuelvo.serviceprovider.domain.notifications.NotificationLocalSession(recreated, RecordingNotificationDisplay(),
            com.loresuelvo.serviceprovider.domain.notifications.NotificationConversationState())
        assertFalse(recreatedLocal.verify(session, 7))
        val recreatedSessions = com.loresuelvo.serviceprovider.data.auth.EncryptedAuthSessionStore(authPreferences, recreatedLocal)
        recreatedSessions.clearSession()
        recreatedSessions.saveSession(session)
        assertTrue(recreatedLocal.verify(session, 7))
        val afterAnotherRestart = EncryptedNotificationStateStore(preferences)
        val afterAnotherRestartLocal = com.loresuelvo.serviceprovider.domain.notifications.NotificationLocalSession(afterAnotherRestart,
            RecordingNotificationDisplay(), com.loresuelvo.serviceprovider.domain.notifications.NotificationConversationState())
        assertTrue(afterAnotherRestartLocal.verify(session, 7))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun newly_published_session_observers_already_see_establishment_after_credential_persistence() = kotlinx.coroutines.test.runTest {
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("push-publication", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        var established = false
        val cleanup = object : com.loresuelvo.serviceprovider.domain.notifications.NotificationSessionCleanup {
            override fun invalidate() { established = false }
            override fun establish(session: com.loresuelvo.serviceprovider.domain.auth.AuthSession) {
                assertEquals(session.accessToken, preferences.getString("access_token", null))
                established = true
            }
        }
        val sessions = com.loresuelvo.serviceprovider.data.auth.EncryptedAuthSessionStore(preferences, cleanup)
        val observations = mutableListOf<Boolean>()
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            sessions.sessionFlow.collect { if (it != null) observations += established }
        }
        sessions.saveSession(com.loresuelvo.serviceprovider.domain.auth.AuthSession(
            com.loresuelvo.serviceprovider.domain.auth.User("provider", "p@example.test"), "jwt"))
        assertEquals(listOf(true), observations)
    }

    @Test fun legacy_inactive_binding_without_session_hash_cannot_reverify_across_restart_until_explicit_establishment() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("push-legacy-logout", Context.MODE_PRIVATE)
        val authPreferences = context.getSharedPreferences("push-legacy-auth", Context.MODE_PRIVATE)
        preferences.edit().clear().commit(); authPreferences.edit().clear().commit()
        val store = EncryptedNotificationStateStore(preferences)
        store.write(store.read().copy(binding = NotificationBinding("legacy", "provider", 7, false, true)))
        val local = com.loresuelvo.serviceprovider.domain.notifications.NotificationLocalSession(store, RecordingNotificationDisplay(),
            com.loresuelvo.serviceprovider.domain.notifications.NotificationConversationState())
        val session = com.loresuelvo.serviceprovider.domain.auth.AuthSession(
            com.loresuelvo.serviceprovider.domain.auth.User("provider", "p@example.test"), "same-jwt")
        assertFalse(local.verify(session, 7))
        val sessions = com.loresuelvo.serviceprovider.data.auth.EncryptedAuthSessionStore(authPreferences, local)
        sessions.saveSession(session)
        assertTrue(local.verify(session, 7))
    }

    @Test fun corrupt_or_missing_storage_disables_notifications_without_inventing_replacement_possession() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("push-corrupt", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("state", "not-json").commit()
        val corrupted = EncryptedNotificationStateStore(preferences)
        assertFalse(corrupted.write(corrupted.read()))
        assertEquals("not-json", preferences.getString("state", null))
        val missing = EncryptedNotificationStateStore(null)
        assertFalse(missing.write(missing.read()))
    }
}
