package com.loresuelvo.serviceprovider.data.auth

import android.content.SharedPreferences
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Encrypted [SharedPreferences]-backed [AuthSessionStore]. Owns the
 * `MutableStateFlow<AuthSession?>` so the in-memory cache and the
 * persisted blob never disagree: every `saveSession` / `clearSession`
 * writes to both, in that order.
 *
 * `@Singleton` so the cached session is shared across the whole
 * process — multiple call sites (the navigation graph, the
 * `SessionViewModel`, the `AuthInterceptor` for the OkHttp client)
 * all observe the SAME flow.
 */
@Singleton
class EncryptedAuthSessionStore @Inject constructor(
    private val preferences: SharedPreferences,
) : AuthSessionStore {

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

    override fun getSession(): AuthSession? = readSession()

    override fun saveSession(session: AuthSession) {
        preferences
            .edit()
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_EMAIL, session.user.email)
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .commit()

        _sessionFlow.value = session
    }

    override fun clearSession() {
        preferences
            .edit()
            .clear()
            .commit()

        _sessionFlow.value = null
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