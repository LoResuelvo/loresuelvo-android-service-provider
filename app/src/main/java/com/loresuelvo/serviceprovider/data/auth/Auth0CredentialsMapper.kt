package com.loresuelvo.serviceprovider.data.auth

import com.auth0.android.result.Credentials
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import javax.inject.Inject
import org.json.JSONObject

/**
 * Decodes the `id_token` JWT in an Auth0 [Credentials] blob into an
 * [AuthSession]. Pure Kotlin — no Android, no Auth0 SDK at runtime,
 * no `Context`.
 *
 * The provider app keeps the [User] projection minimal in Fase 1
 * (`id` + `email`); richer profile fields land alongside the
 * Provider onboarding feature.
 */
class Auth0CredentialsMapper @Inject constructor() {

    fun toSession(credentials: Credentials): AuthSession? {
        val claims = decodeJwtClaims(credentials.idToken) ?: return null
        val sub = claims.optString("sub").takeIf { it.isNotBlank() } ?: return null
        val email = claims.optString("email").orEmpty()
        return AuthSession(
            user = User(id = sub, email = email),
            accessToken = credentials.accessToken,
        )
    }

    private fun decodeJwtClaims(jwt: String): JSONObject? {
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val decoded = android.util.Base64.decode(
                parts[1],
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING,
            )
            JSONObject(String(decoded, Charsets.UTF_8))
        }.getOrNull()
    }
}