package com.loresuelvo.serviceprovider.domain.usecase.auth

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class EstablishAuthSessionUseCaseTest {

    @Test
    fun saves_the_authenticated_session_once() {
        val sessionStore = RecordingAuthSessionStore()
        val session = AuthSession(
            user = User(
                id = "auth0|provider",
                email = "provider@example.com",
            ),
            accessToken = "synthetic-provider-access-token",
        )

        EstablishAuthSessionUseCase(sessionStore)(session)

        assertEquals(session, sessionStore.getSession())
        assertEquals(1, sessionStore.saveCalls)
    }

    private class RecordingAuthSessionStore : AuthSessionStore {

        private val state = MutableStateFlow<AuthSession?>(null)
        override val sessionFlow: StateFlow<AuthSession?> = state

        var saveCalls: Int = 0
            private set

        override fun getSession(): AuthSession? = state.value

        override fun saveSession(session: AuthSession) {
            saveCalls += 1
            state.value = session
        }

        override fun clearSession() {
            state.value = null
        }
    }
}
