package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AuthInterceptorTest {

    @Test
    fun adds_the_shared_session_token_to_authenticated_requests() {
        val session = AuthSession(
            user = User(id = "auth0|provider", email = "provider@example.com"),
            accessToken = "synthetic-provider-access-token",
        )
        val sessionStore = InMemoryAuthSessionStore(session)
        val interceptor = AuthInterceptor(sessionStore)
        val original = Request.Builder()
            .url("https://api.synthetic.loresuelvo.test/profile")
            .build()
        val chain = mockk<Interceptor.Chain>()
        val capturedRequest = slot<Request>()
        val response = Response.Builder()
            .request(original)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        every { chain.request() } returns original
        every { chain.proceed(capture(capturedRequest)) } returns response

        assertSame(response, interceptor.intercept(chain))
        assertEquals(
            "Bearer ${session.accessToken}",
            capturedRequest.captured.header("Authorization"),
        )
    }

    private class InMemoryAuthSessionStore(session: AuthSession) : AuthSessionStore {

        private val state = MutableStateFlow<AuthSession?>(session)
        override val sessionFlow: StateFlow<AuthSession?> = state

        override fun getSession(): AuthSession? = state.value

        override fun saveSession(session: AuthSession) {
            state.value = session
        }

        override fun clearSession() {
            state.value = null
        }
    }
}
