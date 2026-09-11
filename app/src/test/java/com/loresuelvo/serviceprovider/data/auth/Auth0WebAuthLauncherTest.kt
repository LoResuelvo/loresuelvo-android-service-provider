package com.loresuelvo.serviceprovider.data.auth

import com.auth0.android.provider.WebAuthProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class Auth0WebAuthLauncherTest {

    @Test
    fun signup_configures_scheme_audience_and_signup_hint() {
        val builder = configuredBuilder()

        builder.configureSignup(config)

        verifyOrder {
            builder.withScheme(SYNTHETIC_SCHEME)
            builder.withAudience(SYNTHETIC_AUDIENCE)
            builder.withParameters(mapOf("screen_hint" to "signup"))
        }
        verify(exactly = 0) { builder.withConnection(any()) }
    }

    @Test
    fun google_login_keeps_its_dedicated_connection() {
        val builder = configuredBuilder()

        builder.configureGoogleLogin(config)

        verify {
            builder.withScheme(SYNTHETIC_SCHEME)
            builder.withAudience(SYNTHETIC_AUDIENCE)
            builder.withConnection(GOOGLE_CONNECTION)
        }
    }

    private fun configuredBuilder(): WebAuthProvider.Builder =
        mockk<WebAuthProvider.Builder>().also { builder ->
            every { builder.withScheme(any()) } returns builder
            every { builder.withAudience(any()) } returns builder
            every { builder.withParameters(any()) } returns builder
            every { builder.withConnection(any()) } returns builder
        }

    private val config = Auth0Config(
        domain = "synthetic.auth0.com",
        clientId = "synthetic-client-id",
        scheme = SYNTHETIC_SCHEME,
        audience = SYNTHETIC_AUDIENCE,
    )

    private companion object {
        const val SYNTHETIC_SCHEME = "com.loresuelvo.provider.synthetic"
        const val SYNTHETIC_AUDIENCE = "https://api.synthetic.loresuelvo.test"
        const val GOOGLE_CONNECTION = "google-oauth2"
    }
}
