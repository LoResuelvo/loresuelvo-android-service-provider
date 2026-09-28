package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import javax.inject.Inject

data class ProviderWorkOrderDetailResult(
    val detail: WorkOrderDetailOutcome,
    val consumer: WorkOrder?,
)

class GetProviderWorkOrderDetailUseCase @Inject constructor(
    private val orders: WorkOrderRepository,
) {
    suspend operator fun invoke(orderId: Int): ProviderWorkOrderDetailResult {
        if (orderId <= 0) return ProviderWorkOrderDetailResult(WorkOrderDetailOutcome.Failure.Invalid, null)
        val detail = orders.getWorkOrder(orderId)
        if (detail !is WorkOrderDetailOutcome.Success) return ProviderWorkOrderDetailResult(detail, null)
        val current = detail.order
        if (current.id != orderId || current.serviceProposalId <= 0 || current.consumerId <= 0 ||
            current.providerId <= 0) {
            return ProviderWorkOrderDetailResult(WorkOrderDetailOutcome.Failure.Invalid, null)
        }
        val summaryResult = orders.getWorkOrders()
        if (summaryResult == ActivityLoadOutcome.Failure.Unauthorized) {
            return ProviderWorkOrderDetailResult(WorkOrderDetailOutcome.Failure.Unauthorized, null)
        }
        val summaries = (summaryResult as? ActivityLoadOutcome.Success)?.items.orEmpty()
        val consumer = summaries.singleOrNull {
            it.id == current.id && it.serviceProposalId == current.serviceProposalId &&
                it.consumerId == current.consumerId
        }
        return ProviderWorkOrderDetailResult(detail, consumer)
    }
}
