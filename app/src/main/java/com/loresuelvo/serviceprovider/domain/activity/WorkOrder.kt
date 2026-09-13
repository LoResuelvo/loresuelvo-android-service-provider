package com.loresuelvo.serviceprovider.domain.activity

data class WorkOrder(
    val id: Int,
    val consumerName: String,
    val description: String,
    val scheduledOn: Long,
    val status: WorkOrderStatus,
)

sealed interface WorkOrderStatus {
    data object Scheduled : WorkOrderStatus
    data object Paid : WorkOrderStatus
    data object AwaitingPayment : WorkOrderStatus
    data class Unsupported(val value: String) : WorkOrderStatus
}
