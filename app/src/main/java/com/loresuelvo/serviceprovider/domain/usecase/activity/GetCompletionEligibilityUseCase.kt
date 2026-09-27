package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.CompletionEligibility
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import javax.inject.Inject

class GetCompletionEligibilityUseCase internal constructor(
    private val orders: WorkOrderRepository,
    private val accounts: CurrentAccountRepository,
    private val nowMillis: () -> Long,
) {
    @Inject constructor(orders: WorkOrderRepository, accounts: CurrentAccountRepository) :
        this(orders, accounts, System::currentTimeMillis)

    suspend operator fun invoke(selected: WorkOrder): CompletionEligibility {
        val detail = when (val result = orders.getWorkOrder(selected.id)) {
            is WorkOrderDetailOutcome.Success -> result.order
            WorkOrderDetailOutcome.Failure.Unauthorized -> return CompletionEligibility.Failure.Unauthorized
            WorkOrderDetailOutcome.Failure.Forbidden -> return CompletionEligibility.Forbidden
            WorkOrderDetailOutcome.Failure.NotFound -> return CompletionEligibility.Failure.NotFound
            is WorkOrderDetailOutcome.Failure.Network -> return CompletionEligibility.Failure.Network(result.cause)
            is WorkOrderDetailOutcome.Failure.Server -> return CompletionEligibility.Failure.Server(result.code)
            WorkOrderDetailOutcome.Failure.Invalid -> return CompletionEligibility.Failure.Invalid
        }
        if (detail.id != selected.id || detail.serviceProposalId != selected.serviceProposalId ||
            detail.consumerId != selected.consumerId) return CompletionEligibility.ChangedOrder

        val account = when (val result = accounts.getCurrentAccount()) {
            is CurrentAccountOutcome.Success -> result.account
            CurrentAccountOutcome.Failure.Unauthorized -> return CompletionEligibility.Failure.Unauthorized
            CurrentAccountOutcome.Failure.NotFound -> return CompletionEligibility.Forbidden
            is CurrentAccountOutcome.Failure.Network -> return CompletionEligibility.Failure.Network(result.cause)
            is CurrentAccountOutcome.Failure.Server -> return CompletionEligibility.Failure.Server(result.code)
            CurrentAccountOutcome.Failure.Invalid -> return CompletionEligibility.Failure.Invalid
        }
        if (account !is CurrentAccount.Provider || account.id != detail.providerId)
            return CompletionEligibility.Forbidden

        return when {
            detail.completionReportId != null -> CompletionEligibility.AlreadyReported
            detail.status is WorkOrderStatus.AwaitingPayment || detail.status is WorkOrderStatus.Paid ->
                CompletionEligibility.AlreadyReported
            detail.status !is WorkOrderStatus.Scheduled -> CompletionEligibility.Unavailable
            nowMillis() < detail.scheduledOn -> CompletionEligibility.TooEarly
            else -> CompletionEligibility.Eligible
        }
    }
}
