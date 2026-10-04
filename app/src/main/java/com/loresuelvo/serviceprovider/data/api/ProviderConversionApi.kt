package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ProviderConversionDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface ProviderConversionApi {
    @GET("providers/me/statistics/conversion")
    @Headers("Cache-Control: no-store")
    suspend fun getConversion(@Query("from") from: String? = null,
        @Query("to") to: String? = null): ProviderConversionDto
}
