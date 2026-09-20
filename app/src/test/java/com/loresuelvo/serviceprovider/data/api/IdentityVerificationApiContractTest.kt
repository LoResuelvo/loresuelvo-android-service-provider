package com.loresuelvo.serviceprovider.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class IdentityVerificationApiContractTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `posts authenticated bodyless request and decodes every field`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"session_id":"9df42484-61d1-4b40-b195-1e2d5e10298f","session_token":"temporary-token","verification_url":"https://verify.didit.me/session","status":"not_started"}""",
            ),
        )

        val dto = api().startIdentityVerification()
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/providers/me/identity-verification-sessions", request.path)
        assertEquals("Bearer synthetic-token", request.getHeader("Authorization"))
        assertEquals(0L, request.bodySize)
        assertEquals("not_started", dto.status)
        assertEquals("https://verify.didit.me/session", dto.verificationUrl)
    }

    private fun api(): BackendApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient.Builder().addInterceptor(AuthInterceptor(SessionStore())).build())
        .addConverterFactory(
            Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()),
        )
        .build()
        .create(BackendApi::class.java)

    private class SessionStore : AuthSessionStore {
        private val state = MutableStateFlow<AuthSession?>(
            AuthSession(User("auth0|provider", "provider@example.com"), "synthetic-token"),
        )
        override val sessionFlow: StateFlow<AuthSession?> = state
        override fun getSession(): AuthSession? = state.value
        override fun saveSession(session: AuthSession) { state.value = session }
        override fun clearSession() { state.value = null }
    }
}
