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
