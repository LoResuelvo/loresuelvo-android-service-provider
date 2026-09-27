package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import javax.inject.Inject

class GetProviderTurnsUseCase @Inject constructor(
    private val repository: WorkOrderRepository,
) {
    suspend operator fun invoke(): ActivityLoadOutcome<WorkOrder> = when (val result = repository.getWorkOrders()) {
        is ActivityLoadOutcome.Success -> ActivityLoadOutcome.Success(
            result.items.sortedWith(compareBy<WorkOrder> { it.scheduledOn }.thenBy { it.id }),
        )
        is ActivityLoadOutcome.Failure -> result
    }
}
