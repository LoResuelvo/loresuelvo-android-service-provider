package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format for backend error responses. The backend documents two
 * shapes — `error-response.yaml` (with `code` + `message`) and
 * `auth-error-response.yaml` (with `error` + `error_description`).
 * We accept both because some handlers merge the two, and we pick the
 * human-readable message in [com.loresuelvo.serviceprovider.data.api.toApiError]
 * with the documented precedence.
 */
@Serializable
data class ApiErrorDto(
    @SerialName("code") val code: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)