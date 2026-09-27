package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.CompletionEligibility
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.category.Category
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetCompletionEligibilityUseCaseTest {
    private val selected = WorkOrder(42, "Ana Pérez", "Repair", 1_000, WorkOrderStatus.Scheduled,
        serviceProposalId = 10, consumerId = 3)
    private val provider = CurrentAccount.Provider(7, "Juan", "Gómez", "juan@example.com",
        Category(1, "Plumbing"), null)

    @Test fun scheduled_owned_order_opens_only_at_or_after_start() = runTest {
        assertEquals(CompletionEligibility.TooEarly, decide(now = 999))
        assertEquals(CompletionEligibility.Eligible, decide(now = 1_000))
        assertEquals(CompletionEligibility.Eligible, decide(now = 1_001))
    }

    @Test fun fresh_detail_overrides_stale_list_status_and_time() = runTest {
        val stale = selected.copy(status = WorkOrderStatus.Paid, scheduledOn = Long.MAX_VALUE)
        assertEquals(CompletionEligibility.Eligible, useCase(FakeOrders(), FakeAccount())(stale))
    }

    @Test fun fresh_order_identity_and_authenticated_provider_must_match() = runTest {
        assertEquals(CompletionEligibility.ChangedOrder, decide(detail = detail().copy(id = 99)))
        assertEquals(CompletionEligibility.ChangedOrder, decide(detail = detail().copy(serviceProposalId = 99)))
        assertEquals(CompletionEligibility.ChangedOrder, decide(detail = detail().copy(consumerId = 99)))
        assertEquals(CompletionEligibility.Forbidden, decide(detail = detail().copy(providerId = 99)))
        assertEquals(CompletionEligibility.Forbidden, decide(account = CurrentAccount.Consumer))
    }

    @Test fun current_status_and_report_presence_block_duplicate_or_unsupported_form() = runTest {
        assertEquals(CompletionEligibility.AlreadyReported, decide(detail = detail().copy(completionReportId = 17)))
        assertEquals(CompletionEligibility.AlreadyReported, decide(detail = detail().copy(status = WorkOrderStatus.AwaitingPayment)))
        assertEquals(CompletionEligibility.AlreadyReported, decide(detail = detail().copy(status = WorkOrderStatus.Paid)))
        assertEquals(CompletionEligibility.Unavailable, decide(detail = detail().copy(status = WorkOrderStatus.Unsupported("new"))))
    }

    @Test fun detail_failures_remain_typed_and_account_is_not_queried() = runTest {
        val account = FakeAccount()
        val network = IOException("offline")
        val failures = mapOf(
            WorkOrderDetailOutcome.Failure.Unauthorized to CompletionEligibility.Failure.Unauthorized,
            WorkOrderDetailOutcome.Failure.Forbidden to CompletionEligibility.Forbidden,
            WorkOrderDetailOutcome.Failure.NotFound to CompletionEligibility.Failure.NotFound,
            WorkOrderDetailOutcome.Failure.Network(network) to CompletionEligibility.Failure.Network(network),
            WorkOrderDetailOutcome.Failure.Server(503) to CompletionEligibility.Failure.Server(503),
            WorkOrderDetailOutcome.Failure.Invalid to CompletionEligibility.Failure.Invalid,
        )
        for ((failure, expected) in failures) {
            assertEquals(expected, useCase(FakeOrders(failure), account) (selected))
        }
        assertEquals(0, account.calls)
    }

    @Test fun account_failures_remain_typed() = runTest {
        val network = IOException("offline")
        val failures = mapOf(
            CurrentAccountOutcome.Failure.Unauthorized to CompletionEligibility.Failure.Unauthorized,
            CurrentAccountOutcome.Failure.NotFound to CompletionEligibility.Forbidden,
            CurrentAccountOutcome.Failure.Network(network) to CompletionEligibility.Failure.Network(network),
            CurrentAccountOutcome.Failure.Server(503) to CompletionEligibility.Failure.Server(503),
            CurrentAccountOutcome.Failure.Invalid to CompletionEligibility.Failure.Invalid,
        )
        for ((failure, expected) in failures) {
            assertEquals(expected, useCase(FakeOrders(), FakeAccount(failure))(selected))
        }
    }

    private suspend fun decide(now: Long = 1_000, detail: WorkOrderDetail = detail(),
        account: CurrentAccount = provider): CompletionEligibility = useCase(
        FakeOrders(WorkOrderDetailOutcome.Success(detail)), FakeAccount(CurrentAccountOutcome.Success(account)), now,
    )(selected)

    private fun useCase(orders: WorkOrderRepository, account: CurrentAccountRepository,
        now: Long = 1_000) = GetCompletionEligibilityUseCase(orders, account) { now }

    private fun detail() = WorkOrderDetail(42, 10, 3, 7, 100, 1_000, "Repair",
        WorkOrderStatus.Scheduled, null)

    private class FakeOrders(private val next: WorkOrderDetailOutcome = WorkOrderDetailOutcome.Success(
        WorkOrderDetail(42, 10, 3, 7, 100, 1_000, "Repair", WorkOrderStatus.Scheduled, null),
    )) : WorkOrderRepository {
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> = error("Not used")
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome = next
    }

    private class FakeAccount(private val next: CurrentAccountOutcome = CurrentAccountOutcome.Success(
        CurrentAccount.Provider(7, "Juan", "Gómez", "juan@example.com", Category(1, "Plumbing"), null),
    )) : CurrentAccountRepository {
        var calls = 0
        override suspend fun getCurrentAccount(): CurrentAccountOutcome { calls++; return next }
    }
}
