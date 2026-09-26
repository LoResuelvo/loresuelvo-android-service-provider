package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.CreateServiceProposalRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.ServiceProposalResponseDto
import com.loresuelvo.serviceprovider.data.api.dto.ServiceProposalListItemDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ServiceProposalApi {
    @GET("service-proposals")
    suspend fun list(): Response<List<ServiceProposalListItemDto>>

    @POST("service-proposals")
    suspend fun create(@Body request: CreateServiceProposalRequestDto): Response<ServiceProposalResponseDto>
}
