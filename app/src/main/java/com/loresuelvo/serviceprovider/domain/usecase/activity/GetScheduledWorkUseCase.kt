package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetScheduledWorkUseCase @Inject constructor(
    private val repository: WorkOrderRepository,
) {
    suspend operator fun invoke(): ActivityLoadOutcome<WorkOrder> = when (
        val outcome = repository.getWorkOrders()
    ) {
        is ActivityLoadOutcome.Success -> ActivityLoadOutcome.Success(
            outcome.items.filter { it.status is WorkOrderStatus.Scheduled },
        )
        is ActivityLoadOutcome.Failure -> outcome
    }
}
