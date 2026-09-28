package com.loresuelvo.serviceprovider.domain.usecase.activity

import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetail
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetProviderWorkOrderDetailUseCaseTest {
    private val detail = WorkOrderDetail(42, 10, 3, 7, 123456, 1000, "Full original reason",
        WorkOrderStatus.Scheduled, null)
    private val summary = WorkOrder(42, "Ana Pérez", "Stale reason", 1, WorkOrderStatus.Paid,
        serviceProposalId = 10, consumerId = 3)

    @Test fun queries_current_detail_and_joins_only_matching_identity() = runTest {
        val repository = FakeOrders(detail, listOf(summary.copy(id = 41), summary))
        val result = GetProviderWorkOrderDetailUseCase(repository)(42)

        assertEquals(WorkOrderDetailOutcome.Success(detail), result.detail)
        assertEquals(summary, result.consumer)
        assertEquals(1, repository.detailCalls)
        assertEquals(1, repository.listCalls)
    }

    @Test fun mismatched_identity_is_omitted_without_losing_authorized_detail() = runTest {
        val repository = FakeOrders(detail, listOf(summary.copy(consumerId = 99)))
        val result = GetProviderWorkOrderDetailUseCase(repository)(42)

        assertEquals(WorkOrderDetailOutcome.Success(detail), result.detail)
        assertNull(result.consumer)
    }

    @Test fun missing_summary_still_preserves_valid_detail() = runTest {
        val repository = FakeOrders(detail, emptyList())
        repository.listFailure = true
        val result = GetProviderWorkOrderDetailUseCase(repository)(42)

        assertEquals(WorkOrderDetailOutcome.Success(detail), result.detail)
        assertNull(result.consumer)
    }

    @Test fun wrong_detail_id_is_invalid_and_does_not_request_summary() = runTest {
        val repository = FakeOrders(detail.copy(id = 99), listOf(summary))
        val result = GetProviderWorkOrderDetailUseCase(repository)(42)

        assertEquals(WorkOrderDetailOutcome.Failure.Invalid, result.detail)
        assertEquals(0, repository.listCalls)
    }

    @Test fun summary_unauthorized_does_not_expose_detail() = runTest {
        val repository = FakeOrders(detail, listOf(summary))
        repository.unauthorized = true
        val result = GetProviderWorkOrderDetailUseCase(repository)(42)

        assertEquals(WorkOrderDetailOutcome.Failure.Unauthorized, result.detail)
        assertNull(result.consumer)
    }

    private class FakeOrders(private val current: WorkOrderDetail, private val summaries: List<WorkOrder>) : WorkOrderRepository {
        var detailCalls = 0
        var listCalls = 0
        var listFailure = false
        var unauthorized = false
        override suspend fun getWorkOrder(id: Int): WorkOrderDetailOutcome {
            detailCalls++
            return WorkOrderDetailOutcome.Success(current)
        }
        override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> {
            listCalls++
            return if (unauthorized) ActivityLoadOutcome.Failure.Unauthorized
                else if (listFailure) ActivityLoadOutcome.Failure.Network(Exception("offline"))
                else ActivityLoadOutcome.Success(summaries)
        }
    }
}
