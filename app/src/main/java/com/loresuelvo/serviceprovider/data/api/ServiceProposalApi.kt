package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.CreateServiceProposalRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.ServiceProposalResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ServiceProposalApi {
    @POST("service-proposals")
    suspend fun create(@Body request: CreateServiceProposalRequestDto): Response<ServiceProposalResponseDto>
}
