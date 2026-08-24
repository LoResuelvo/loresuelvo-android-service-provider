package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ApiErrorDto
import com.loresuelvo.serviceprovider.domain.api.ApiError
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Translates the raw exceptions thrown by Retrofit / OkHttp into the
 * domain [ApiError] hierarchy. The function is `internal` so use
 * cases and repositories can call it without exposing transport
 * details across module boundaries; the only HTTP-aware code lives
 * in `data/api/`.
 */

/**
 * Maps a [Throwable] from the HTTP layer to [ApiError]. Use this
 * for any exception bubbling out of a Retrofit suspend call. The
 * [io.IOException] branch covers the full transport family
 * (timeouts, DNS, connection refused, TLS handshake failures, …).
 */
internal fun Throwable.toApiError(): ApiError = when (this) {
    is HttpException -> toApiError()
    is IOException -> ApiError.Network(this)
    else -> ApiError.Unknown(this)
}

/**
 * Maps an [HttpException] to [ApiError], parsing the body via [json]
 * when present. The body shape is documented in
 * `openapi/components/schemas/error-response.yaml` and
 * `openapi/components/schemas/auth-error-response.yaml`.
 *
 * Precedence for the human-readable message: body `message` > body
 * `error` > status-line message. This matches the spec where
 * `error` is a code (e.g. `invalid_token`) and `message` is the
 * human text; callers that want the code can read it from the
 * exception cause chain if needed.
 */
internal fun HttpException.toApiError(json: Json = ApiErrorMapperDefaults.json): ApiError {
    val code = code()
    val bodyText = response()?.errorBody()?.string()
    val parsed = bodyText?.let { runCatching { json.decodeFromString(ApiErrorDto.serializer(), it) }.getOrNull() }
    val message = parsed?.message?.takeIf { it.isNotBlank() }
        ?: parsed?.error?.takeIf { it.isNotBlank() }
        ?: message().orEmpty()
    return when (code) {
        401 -> ApiError.Unauthorized(message.ifBlank { "Unauthorized" })
        else -> ApiError.Server(code = code, errorMessage = message)
    }
}

internal object ApiErrorMapperDefaults {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}