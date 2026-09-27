package com.loresuelvo.serviceprovider.domain.activity

data class WorkOrderDetail(
    val id: Int,
    val serviceProposalId: Int,
    val consumerId: Int,
    val providerId: Int,
    val amountCents: Long,
    val scheduledOn: Long,
    val description: String,
    val status: WorkOrderStatus,
    val completionReportId: Int?,
)

sealed interface WorkOrderDetailOutcome {
    data class Success(val order: WorkOrderDetail) : WorkOrderDetailOutcome

    sealed interface Failure : WorkOrderDetailOutcome {
        data object Unauthorized : Failure
        data object Forbidden : Failure
        data object NotFound : Failure
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int) : Failure
        data object Invalid : Failure
    }
}
