package com.loresuelvo.serviceprovider.domain.activity

data class WorkOrder(
    val id: Int,
    val consumerName: String,
    val description: String,
    val scheduledOn: Long,
    val status: WorkOrderStatus,
    val amountCents: Long = 0,
    val consumerGivenName: String = consumerName,
    val consumerSurname: String = "",
    val consumerPhotoUrl: String? = null,
    val serviceProposalId: Int = 0,
    val consumerId: Int = 0,
)

sealed interface WorkOrderStatus {
    data object Scheduled : WorkOrderStatus
    data object Paid : WorkOrderStatus
    data object AwaitingPayment : WorkOrderStatus
    data class Unsupported(val value: String) : WorkOrderStatus
}
