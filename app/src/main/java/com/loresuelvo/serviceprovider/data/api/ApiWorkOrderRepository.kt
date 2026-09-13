package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
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
}
