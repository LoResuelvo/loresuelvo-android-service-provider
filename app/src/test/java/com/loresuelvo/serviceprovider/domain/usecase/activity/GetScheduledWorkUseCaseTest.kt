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
    fun includes_current_instant_and_orders_upcoming_work_by_date_then_id() = runTest {
        val now = Instant.parse("2026-09-26T12:00:00Z")
        val orders = listOf(
            WorkOrder(5, "Ana", "Later", now.plusSeconds(60).toEpochMilli(), WorkOrderStatus.Scheduled),
            WorkOrder(9, "Ana", "Equal", now.toEpochMilli(), WorkOrderStatus.Scheduled),
            WorkOrder(2, "Ana", "Equal", now.toEpochMilli(), WorkOrderStatus.Scheduled),
            WorkOrder(1, "Ana", "Past", now.minusMillis(1).toEpochMilli(), WorkOrderStatus.Scheduled),
            WorkOrder(3, "Ana", "Paid", now.plusSeconds(60).toEpochMilli(), WorkOrderStatus.Paid),
            WorkOrder(4, "Ana", "Awaiting", now.plusSeconds(60).toEpochMilli(), WorkOrderStatus.AwaitingPayment),
        )
        val useCase = GetScheduledWorkUseCase(object : WorkOrderRepository {
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(orders)
        }) { now.toEpochMilli() }

        assertEquals(listOf(2, 9, 5), (useCase() as ActivityLoadOutcome.Success).items.map { it.id })
    }

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
            nowMillis = { Instant.parse("2026-09-19T00:00:00Z").toEpochMilli() },
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
