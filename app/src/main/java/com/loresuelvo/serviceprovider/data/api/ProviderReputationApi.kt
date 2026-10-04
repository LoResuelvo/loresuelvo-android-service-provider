package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ProviderReputationDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface ProviderReputationApi {
    @GET("providers/me/statistics/reputation")
    @Headers("Cache-Control: no-store")
    suspend fun getReputation(@Query("limit") limit: Int, @Query("cursor") cursor: String? = null): ProviderReputationDto
}
