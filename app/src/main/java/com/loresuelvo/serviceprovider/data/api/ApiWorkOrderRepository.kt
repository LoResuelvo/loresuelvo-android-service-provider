package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.PostCompletionReportOutcome
import com.loresuelvo.serviceprovider.data.api.dto.PostCompletionReportRequestDto
import com.loresuelvo.serviceprovider.domain.api.ApiError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class ApiWorkOrderRepository @Inject constructor(
    private val backendApi: BackendApi,
) : WorkOrderRepository {

    override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = try {
        ActivityLoadOutcome.Success(backendApi.getWorkOrders().map { it.toDomain() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        when (val error = e.toApiError()) {
            is ApiError.Network -> ActivityLoadOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized -> ActivityLoadOutcome.Failure.Unauthorized
            is ApiError.Server -> ActivityLoadOutcome.Failure.Server(error.code)
            is ApiError.Unknown -> ActivityLoadOutcome.Failure.Invalid
        }
    }

    override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome = try {
        WorkOrderDetailOutcome.Success(backendApi.getWorkOrder(id).toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        when (val error = e.toApiError()) {
            is ApiError.Network -> WorkOrderDetailOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized -> WorkOrderDetailOutcome.Failure.Unauthorized
            is ApiError.Server -> when (error.code) {
                403 -> WorkOrderDetailOutcome.Failure.Forbidden
                404 -> WorkOrderDetailOutcome.Failure.NotFound
                else -> WorkOrderDetailOutcome.Failure.Server(error.code)
            }
            is ApiError.Unknown -> WorkOrderDetailOutcome.Failure.Invalid
        }
    }

    override suspend fun postCompletionReport(
        orderId: Int,
        description: String,
        confirmedFileIds: List<String>,
    ): PostCompletionReportOutcome = try {
        val report = backendApi.postCompletionReport(
            orderId,
            PostCompletionReportRequestDto(description, confirmedFileIds),
        )
        if (report.id > 0) PostCompletionReportOutcome.Success(report.id)
        else PostCompletionReportOutcome.Uncertain.InvalidResponse
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        when (val error = e.toApiError()) {
            is ApiError.Network -> PostCompletionReportOutcome.Uncertain.Network
            is ApiError.Unauthorized -> PostCompletionReportOutcome.Rejected.Unauthorized
            is ApiError.Server -> when (error.code) {
                400 -> PostCompletionReportOutcome.Rejected.InvalidData
                403 -> PostCompletionReportOutcome.Rejected.Forbidden
                404 -> PostCompletionReportOutcome.Rejected.NotFound
                409 -> PostCompletionReportOutcome.Rejected.Conflict
                in 500..599 -> PostCompletionReportOutcome.Uncertain.Server(error.code)
                else -> PostCompletionReportOutcome.Rejected.Other(error.code)
            }
            is ApiError.Unknown -> PostCompletionReportOutcome.Uncertain.InvalidResponse
        }
    }
}
