package com.loresuelvo.serviceprovider.domain.activity

interface WorkOrderRepository {
    suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder>
}
