package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetScheduledWorkUseCase internal constructor(
    private val repository: WorkOrderRepository,
    private val nowMillis: () -> Long,
) {
    @Inject constructor(repository: WorkOrderRepository) : this(repository, System::currentTimeMillis)

    suspend operator fun invoke(): ActivityLoadOutcome<WorkOrder> = when (
        val outcome = repository.getWorkOrders()
    ) {
        is ActivityLoadOutcome.Success -> {
            val now = nowMillis()
            ActivityLoadOutcome.Success(outcome.items.filter {
                it.status is WorkOrderStatus.Scheduled && it.scheduledOn >= now
            }.sortedWith(compareBy<WorkOrder> { it.scheduledOn }.thenBy { it.id }))
        }
        is ActivityLoadOutcome.Failure -> outcome
    }
}
