package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetProviderTurnsUseCaseTest {
    @Test fun retains_past_orders_and_sorts_by_instant_then_id() = runTest {
        val orders = listOf(
            WorkOrder(12, "Ana", "d", 30, WorkOrderStatus.Scheduled),
            WorkOrder(30, "Ana", "b", 20, WorkOrderStatus.Scheduled),
            WorkOrder(40, "Ana", "a", 10, WorkOrderStatus.Paid),
            WorkOrder(11, "Ana", "c", 30, WorkOrderStatus.AwaitingPayment),
        )
        val repository = object : WorkOrderRepository {
            override suspend fun getWorkOrders() = ActivityLoadOutcome.Success(orders)
        }

        val result = GetProviderTurnsUseCase(repository)()

        assertEquals(listOf(40, 30, 11, 12), (result as ActivityLoadOutcome.Success<WorkOrder>).items.map { it.id })
    }
}
