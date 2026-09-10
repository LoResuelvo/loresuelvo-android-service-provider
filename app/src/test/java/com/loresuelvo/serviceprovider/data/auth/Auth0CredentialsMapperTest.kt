package com.loresuelvo.serviceprovider.data.auth

import com.auth0.android.result.Credentials
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import java.util.Base64
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Auth0CredentialsMapperTest {

    private val mapper = Auth0CredentialsMapper()

    @Test
    fun should_map_credentials_with_identity_and_access_token() {
        val session = mapper.toSession(
            credentials(idToken = jwtClaims("sub" to "auth0|provider", "email" to "provider@example.com")),
        )

        assertEquals(
            AuthSession(
                user = User(id = "auth0|provider", email = "provider@example.com"),
                accessToken = "access-token",
            ),
            session,
        )
    }

    @Test
    fun should_reject_malformed_id_token() {
        assertNull(mapper.toSession(credentials(idToken = "not-a-jwt")))
    }

    @Test
    fun should_reject_id_token_without_subject_claim() {
        assertNull(mapper.toSession(credentials(idToken = jwtClaims("email" to "provider@example.com"))))
    }

    @Test
    fun should_reject_id_token_with_blank_subject_claim() {
        assertNull(mapper.toSession(credentials(idToken = jwtClaims("sub" to "   "))))
    }

    @Test
    fun should_reject_credentials_with_empty_access_token() {
        assertNull(
            mapper.toSession(
                credentials(
                    idToken = jwtClaims("sub" to "auth0|provider"),
                    accessToken = "",
                ),
            ),
        )
    }

    @Test
    fun should_reject_credentials_with_blank_access_token() {
        assertNull(
            mapper.toSession(
                credentials(
                    idToken = jwtClaims("sub" to "auth0|provider"),
                    accessToken = "   ",
                ),
            ),
        )
    }

    private fun credentials(
        idToken: String,
        accessToken: String = "access-token",
    ): Credentials = Credentials(
        idToken,
        accessToken,
        "Bearer",
        null,
        Date(0),
        "openid",
    )

    private fun jwtClaims(vararg claims: Pair<String, String>): String {
        val payload = claims.joinToString(",", prefix = "{", postfix = "}") { (name, value) ->
            "\"$name\":\"$value\""
        }
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf("{\"alg\":\"none\"}", payload, "signature")
            .joinToString(".") { part -> encoder.encodeToString(part.toByteArray()) }
    }
}
