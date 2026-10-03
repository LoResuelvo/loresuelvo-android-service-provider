package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.CollectionTransactionsDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface CollectionTransactionsApi {
    @GET("providers/me/statistics/collections/transactions")
    @Headers("Cache-Control: no-store")
    suspend fun getTransactions(@Query("from") from: String, @Query("to") to: String,
        @Query("purpose") purpose: String?, @Query("limit") limit: Int,
        @Query("cursor") cursor: String?): CollectionTransactionsDto
}
