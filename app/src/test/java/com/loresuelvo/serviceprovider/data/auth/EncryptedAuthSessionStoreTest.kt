package com.loresuelvo.serviceprovider.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.SessionClearOutcome
import com.loresuelvo.serviceprovider.domain.auth.User
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EncryptedAuthSessionStoreTest {
    private lateinit var preferences: SharedPreferences
    private val session = AuthSession(User("auth0|test", "test@example.test"), "synthetic-token")

    @Before fun setup() {
        preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("logout-subject", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @Test fun successful_logout_removes_persisted_credentials_and_recreated_store_is_unauthenticated() {
        val store = EncryptedAuthSessionStore(preferences)
        store.saveSession(session)
        assertEquals(SessionClearOutcome.Cleared, store.clearSessionDurably())
        assertNull(store.getSession())
        assertNull(store.sessionFlow.value)
        assertTrue(preferences.all.isEmpty())
        assertNull(EncryptedAuthSessionStore(preferences).getSession())
    }

    @Test fun failed_commit_blocks_reads_and_reports_durability_failure_until_retry() {
        verifyRemovalFailure(throws = false)
    }

    @Test fun storage_exception_blocks_reads_and_reports_durability_failure_until_retry() {
        verifyRemovalFailure(throws = true)
    }

    @Test fun expiration_cleanup_remains_nonthrowing_when_persistence_fails() {
        EncryptedAuthSessionStore(preferences).saveSession(session)
        val failing = EncryptedAuthSessionStore(failingPreferences { false })
        failing.clearSession()
        assertNull(failing.getSession())
        assertNull(failing.sessionFlow.value)
    }

    @Test fun every_shared_clear_and_session_replacement_invalidates_notices_before_credentials_change() {
        val observedSessions = mutableListOf<AuthSession?>()
        lateinit var store: EncryptedAuthSessionStore
        store = EncryptedAuthSessionStore(preferences,
            com.loresuelvo.serviceprovider.domain.notifications.NotificationSessionCleanup { observedSessions += store.getSession() })
        store.saveSession(session)
        assertEquals(listOf<AuthSession?>(null), observedSessions)
        store.saveSession(session)
        assertEquals(1, observedSessions.size)
        store.clearSession()
        assertEquals(session, observedSessions.last())
        assertNull(store.getSession())
        store.saveSession(session)
        val replacement = AuthSession(User("other", "other@example.test"), "other-token")
        store.saveSession(replacement)
        assertEquals(session, observedSessions.last())
        store.clearSessionDurably()
        assertEquals(replacement, observedSessions.last())
        assertNull(store.getSession())
    }

    @Test fun expected_session_clear_preserves_replacement_credentials_and_notification_binding() {
        var invalidations = 0
        val store = EncryptedAuthSessionStore(preferences,
            com.loresuelvo.serviceprovider.domain.notifications.NotificationSessionCleanup { invalidations++ })
        store.saveSession(session)
        val replacement = AuthSession(User("replacement", "replacement@example.test"), "replacement-token")
        store.saveSession(replacement)
        val before = invalidations
        assertEquals(SessionClearOutcome.Cleared, store.clearSessionDurably(session))
        assertEquals(replacement, store.getSession())
        assertEquals(replacement, EncryptedAuthSessionStore(preferences).getSession())
        assertEquals(before, invalidations)
        assertEquals(SessionClearOutcome.Cleared, store.clearSessionDurably(replacement))
        assertNull(store.getSession())
        assertEquals(before + 1, invalidations)
    }

    private fun verifyRemovalFailure(throws: Boolean) {
        EncryptedAuthSessionStore(preferences).saveSession(session)
        var fail = true
        val store = EncryptedAuthSessionStore(failingPreferences {
            if (!fail) true else if (throws) throw IllegalStateException("synthetic storage failure") else false
        })
        assertEquals(session, store.getSession())
        assertEquals(SessionClearOutcome.PersistenceFailure, store.clearSessionDurably())
        assertNull(store.getSession())
        assertNull(store.sessionFlow.value)
        assertFalse(preferences.all.isEmpty())
        fail = false
        assertEquals(SessionClearOutcome.Cleared, store.clearSessionDurably())
        assertTrue(preferences.all.isEmpty())
        assertNull(EncryptedAuthSessionStore(preferences).getSession())
    }

    private fun failingPreferences(canCommit: () -> Boolean): SharedPreferences =
        object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val editor = preferences.edit()
                return object : SharedPreferences.Editor by editor {
                    override fun clear(): SharedPreferences.Editor { editor.clear(); return this }
                    override fun commit(): Boolean = canCommit() && editor.commit()
                }
            }
        }
}
