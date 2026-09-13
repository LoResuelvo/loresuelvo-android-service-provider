package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.JobRequestRequesterDto
import com.loresuelvo.serviceprovider.data.api.dto.JobRequestSummaryDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderCounterpartDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderSummaryDto
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityMapperTest {

    @Test
    fun maps_job_request_consumer_and_summary_fields() {
        val mapped = JobRequestSummaryDto(
            id = 7,
            conversationId = 8,
            title = "Reparar pérdida",
            description = "Pérdida debajo de la pileta",
            status = "pending",
            requester = JobRequestRequesterDto("Ana", "Pérez"),
        ).toDomain()

        assertEquals(7, mapped.id)
        assertEquals("Ana Pérez", mapped.consumerName)
        assertEquals("Reparar pérdida", mapped.title)
        assertEquals("Pérdida debajo de la pileta", mapped.description)
    }

    @Test
    fun maps_scheduled_work_and_tolerates_awaiting_payment_status() {
        val scheduled = workOrder(status = "scheduled").toDomain()
        val awaitingPayment = workOrder(status = "awaiting_payment").toDomain()

        assertEquals(Instant.parse("2026-09-20T15:00:00Z").toEpochMilli(), scheduled.scheduledOn)
        assertEquals("Carlos López", scheduled.consumerName)
        assertEquals(WorkOrderStatus.Scheduled, scheduled.status)
        assertEquals(WorkOrderStatus.AwaitingPayment, awaitingPayment.status)
    }

    @Test
    fun preserves_unknown_work_status_without_crashing() {
        val mapped = workOrder(status = "new_status").toDomain()

        assertTrue(mapped.status is WorkOrderStatus.Unsupported)
        assertEquals("new_status", (mapped.status as WorkOrderStatus.Unsupported).value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_malformed_scheduled_date() {
        workOrder(status = "scheduled", scheduledOn = "not-a-date").toDomain()
    }

    private fun workOrder(
        status: String,
        scheduledOn: String = "2026-09-20T15:00:00Z",
    ) = WorkOrderSummaryDto(
        id = 9,
        serviceProposalId = 10,
        amountCents = 25000,
        scheduledOn = scheduledOn,
        description = "Instalar una canilla",
        status = status,
        acceptedOn = "2026-09-19T10:00:00Z",
        counterpart = WorkOrderCounterpartDto(
            id = 11,
            role = "consumer",
            name = "Carlos",
            surname = "López",
        ),
    )
}
