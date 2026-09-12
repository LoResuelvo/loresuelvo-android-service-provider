package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.CategoryDto
import com.loresuelvo.serviceprovider.data.api.dto.ProviderSummaryDto
import com.loresuelvo.serviceprovider.data.api.dto.RegisterProviderRequestDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit-typed contract for the backend's provider endpoints. The
 * interface is intentionally narrow: only the calls this app needs.
 * Adding a new endpoint here is the only way to add a new network
 * operation; the use cases never see Retrofit types.
 *
 * Wire paths mirror `loresuelvo-api/internal/adapters/http/router.go`.
 */
interface BackendApi {

    /**
     * `GET /categories` — the platform's service categories. Public
     * (no auth required), so it can be called before the user signs
     * in. Returns a JSON array of [CategoryDto]; non-2xx throws
     * [retrofit2.HttpException], mapped by the data layer to
     * [com.loresuelvo.serviceprovider.domain.api.ApiError].
     */
    @GET("categories")
    suspend fun getCategories(): List<CategoryDto>

    /**
     * `POST /providers` — creates the provider record on the platform.
     * Authenticated endpoint requiring session bearer token.
     */
    @POST("providers")
    suspend fun registerProvider(
        @Body request: RegisterProviderRequestDto,
    ): ProviderSummaryDto
}