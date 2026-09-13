package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetScheduledWorkUseCaseTest {

    @Test
    fun returns_only_scheduled_work_orders() = runTest {
        val useCase = GetScheduledWorkUseCase(
            repository = object : WorkOrderRepository {
                override suspend fun getWorkOrders(): ActivityLoadOutcome<com.loresuelvo.serviceprovider.domain.activity.WorkOrder> =
                    ActivityLoadOutcome.Success(
                        listOf(
                            workOrder(WorkOrderStatus.Scheduled),
                            workOrder(WorkOrderStatus.Paid),
                            workOrder(WorkOrderStatus.AwaitingPayment),
                            workOrder(WorkOrderStatus.Unsupported("new_status")),
                        ),
                    )
            },
        )

        val outcome = useCase()

        assertEquals(
            listOf(1),
            (outcome as ActivityLoadOutcome.Success).items.map { it.id },
        )
    }

    private fun workOrder(status: WorkOrderStatus) = WorkOrder(
        id = if (status is WorkOrderStatus.Scheduled) 1 else 2,
        consumerName = "Ana Pérez",
        description = "Trabajo",
        scheduledOn = Instant.parse("2026-09-20T15:00:00Z").toEpochMilli(),
        status = status,
    )
}
