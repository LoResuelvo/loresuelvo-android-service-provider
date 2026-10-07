package com.loresuelvo.serviceprovider.data.auth

import android.content.SharedPreferences
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import com.loresuelvo.serviceprovider.domain.auth.SessionClearOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Encrypted [SharedPreferences]-backed [AuthSessionStore]. Owns the
 * `MutableStateFlow<AuthSession?>` used by every session consumer. Clearing
 * blocks in-memory access before durable removal; a failed write is reported
 * so callers can retry without exposing stale persisted credentials.
 *
 * `@Singleton` so the cached session is shared across the whole
 * process — multiple call sites (the navigation graph, the
 * `SessionViewModel`, the `AuthInterceptor` for the OkHttp client)
 * all observe the SAME flow.
 */
@Singleton
class EncryptedAuthSessionStore @Inject constructor(
    private val preferences: SharedPreferences,
    private val notificationCleanup: com.loresuelvo.serviceprovider.domain.notifications.NotificationSessionCleanup? = null,
) : AuthSessionStore {
    private val sessionWrites = Any()

    private val _sessionFlow: MutableStateFlow<AuthSession?> =
        MutableStateFlow(readSession())

    override val sessionFlow: StateFlow<AuthSession?> = _sessionFlow.asStateFlow()

    init {
        // Defensive: the StateFlow already mirrors `readSession()`. If
        // the SharedPreferences were stale (write from another process
        // or a crash between write and `setValue`), this `init` is a
        // no-op because we read at construction time.
        _sessionFlow.update { it ?: readSession() }
    }

    override fun getSession(): AuthSession? = _sessionFlow.value

    override fun saveSession(session: AuthSession) = synchronized(sessionWrites) {
        if (_sessionFlow.value != session) notificationCleanup?.invalidate()
        preferences
            .edit()
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_EMAIL, session.user.email)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .commit()

        _sessionFlow.value = session
    }

    override fun clearSession() {
        clearSessionDurably()
    }

    override fun clearSessionDurably(expectedSession: AuthSession?): SessionClearOutcome = synchronized(sessionWrites) {
        if (_sessionFlow.value != expectedSession) SessionClearOutcome.Cleared else clearSessionDurably()
    }

    override fun clearSessionDurably(): SessionClearOutcome = synchronized(sessionWrites) {
        notificationCleanup?.invalidate()
        _sessionFlow.value = null
        val removed = try {
            preferences.edit().clear().commit()
        } catch (_: RuntimeException) {
            false
        }
        if (removed) SessionClearOutcome.Cleared else SessionClearOutcome.PersistenceFailure
    }

    private fun readSession(): AuthSession? {
        val userId = preferences
            .getString(KEY_USER_ID, null)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val accessToken = preferences.getString(KEY_ACCESS_TOKEN, null)
            ?: return null

        return AuthSession(
            user = User(
                id = userId,
                email = preferences.getString(KEY_EMAIL, null).orEmpty(),
            ),
            accessToken = accessToken,
        )
    }

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_ACCESS_TOKEN = "access_token"
    }
}
