package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ProviderActivityDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface ProviderActivityApi {
    @GET("providers/me/statistics/activity")
    @Headers("Cache-Control: no-store")
    suspend fun getActivity(@Query("from") from: String, @Query("to") to: String,
        @Query("granularity") granularity: String, @Query("compare_previous") comparePrevious: Boolean): ProviderActivityDto
}
