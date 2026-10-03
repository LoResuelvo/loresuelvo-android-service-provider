package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ProviderCollectionsDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface ProviderCollectionsApi {
    @GET("providers/me/statistics/collections")
    @Headers("Cache-Control: no-store")
    suspend fun getCollections(@Query("from") from: String, @Query("to") to: String,
        @Query("granularity") granularity: String, @Query("compare_previous") comparePrevious: Boolean): ProviderCollectionsDto
}
