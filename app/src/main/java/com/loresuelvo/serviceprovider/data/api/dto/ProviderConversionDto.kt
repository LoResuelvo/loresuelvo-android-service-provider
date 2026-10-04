package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderConversionDto(val period: ConversionPeriodDto,
    @SerialName("observed_at") val observedAt: String,
    val proposals: ProposalConversionDto, val requests: RequestAcceptanceDto)
@Serializable
data class ConversionPeriodDto(val from: String, val to: String,
    @SerialName("time_zone") val timeZone: String)
@Serializable
data class ConversionRatioDto(val numerator: Long, val denominator: Long, val percentage: Double?)
@Serializable
data class ConversionStageRatesDto(val cohort: ConversionRatioDto,
    @SerialName("previous_stage") val previousStage: ConversionRatioDto)
@Serializable
data class ProposalStagesDto(val issued: Long, val contracted: Long, val reported: Long, val paid: Long)
@Serializable
data class ProposalRatesDto(val contracted: ConversionStageRatesDto, val reported: ConversionStageRatesDto,
    val paid: ConversionStageRatesDto)
@Serializable
data class ProposalConversionDto(val stages: ProposalStagesDto, val rates: ProposalRatesDto,
    val uncontracted: Long)
@Serializable
data class RequestAcceptanceDto(val received: Long, val accepted: Long, val pending: Long,
    @SerialName("acceptance_rate") val acceptanceRate: ConversionRatioDto)
