package com.loresuelvo.serviceprovider.data.auth

import com.auth0.android.result.Credentials
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.User
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        if (credentials.accessToken.isBlank()) return null
        val claims = decodeJwtClaims(credentials.idToken) ?: return null
        val sub = claims["sub"]?.takeIf { it.isNotBlank() } ?: return null
        val email = claims["email"].orEmpty()
        return AuthSession(
            user = User(id = sub, email = email),
            accessToken = credentials.accessToken,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeJwtClaims(jwt: String): Map<String, String>? {
        val parts = jwt.split('.')
        if (parts.size < 2) return null
        return runCatching {
            val decoded = Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(parts[1])
            Json.parseToJsonElement(String(decoded, Charsets.UTF_8))
                .jsonObject
                .mapValues { (_, value) -> value.jsonPrimitive.content }
        }.getOrNull()
    }
}
