package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProviderActivityDto(val period: ActivityPeriodDto,
    @SerialName("calculated_at") val calculatedAt: String,
    val results: ActivityResultsDto, val evolution: List<ActivityBucketDto>,
    @SerialName("current_pending") val currentPending: CurrentPendingDto,
    val comparison: ActivityComparisonDto? = null)
@Serializable
data class ActivityPeriodDto(val from: String, val to: String, val granularity: String,
    @SerialName("time_zone") val timeZone: String)
@Serializable
data class ActivityResultsDto(
    @SerialName("confirmed_bookings") val confirmedBookings: Long,
    @SerialName("reported_completions") val reportedCompletions: Long,
    @SerialName("fully_paid_work_orders") val fullyPaidWorkOrders: Long,
    @SerialName("clients_served") val clientsServed: Long,
    @SerialName("new_clients") val newClients: Long,
    @SerialName("returning_clients") val returningClients: Long,
    @SerialName("agreed_value_cents") val agreedValueCents: Long,
    @SerialName("average_value_cents") val averageValueCents: Long?, val currency: String)
@Serializable
data class ActivityBucketDto(val from: String, val to: String,
    @SerialName("confirmed_bookings") val confirmedBookings: Long,
    @SerialName("reported_completions") val reportedCompletions: Long,
    @SerialName("fully_paid_work_orders") val fullyPaidWorkOrders: Long)
@Serializable
data class CurrentPendingDto(val requests: Long,
    @SerialName("scheduled_orders") val scheduledOrders: Long,
    @SerialName("awaiting_payment_orders") val awaitingPaymentOrders: Long)
@Serializable
data class ActivityComparisonDto(val period: ActivityPeriodDto, val results: ActivityResultsDto,
    val changes: ActivityChangesDto)
@Serializable
data class ActivityChangeDto(val absolute: Long?, val percentage: Double?)
@Serializable
data class ActivityChangesDto(
    @SerialName("confirmed_bookings") val confirmedBookings: ActivityChangeDto,
    @SerialName("reported_completions") val reportedCompletions: ActivityChangeDto,
    @SerialName("fully_paid_work_orders") val fullyPaidWorkOrders: ActivityChangeDto,
    @SerialName("clients_served") val clientsServed: ActivityChangeDto,
    @SerialName("new_clients") val newClients: ActivityChangeDto,
    @SerialName("returning_clients") val returningClients: ActivityChangeDto,
    @SerialName("agreed_value_cents") val agreedValueCents: ActivityChangeDto,
    @SerialName("average_value_cents") val averageValueCents: ActivityChangeDto)
