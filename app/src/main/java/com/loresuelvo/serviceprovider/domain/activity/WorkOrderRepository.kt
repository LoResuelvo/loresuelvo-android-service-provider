package com.loresuelvo.serviceprovider.domain.activity

interface WorkOrderRepository {
    suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder>
    suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome =
        error("Current work-order detail is not configured")

    suspend fun postCompletionReport(
        orderId: Int,
        description: String,
        confirmedFileIds: List<String>,
    ): PostCompletionReportOutcome = error("Completion report submission is not configured")
}
