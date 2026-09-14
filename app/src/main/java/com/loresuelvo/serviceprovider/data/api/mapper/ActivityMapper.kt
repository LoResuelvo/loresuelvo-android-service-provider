package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.JobRequestRequesterDto
import com.loresuelvo.serviceprovider.data.api.dto.JobRequestSummaryDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderCounterpartDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderSummaryDto
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus

internal fun JobRequestSummaryDto.toDomain(): JobRequest = JobRequest(
    id = id,
    consumerName = requester.fullName(),
    title = title,
    description = description,
    images = images.map { image ->
        JobRequestImage(
            id = image.id,
            url = image.url,
            originalName = image.originalName,
        )
    },
)

internal fun WorkOrderSummaryDto.toDomain(): WorkOrder = WorkOrder(
    id = id,
    consumerName = counterpart.fullName(),
    description = description,
    scheduledOn = scheduledOn.toEpochMillis(),
    status = status.toDomainStatus(),
)

private fun String.toDomainStatus(): WorkOrderStatus = when (lowercase()) {
    "scheduled" -> WorkOrderStatus.Scheduled
    "paid" -> WorkOrderStatus.Paid
    "awaiting_payment" -> WorkOrderStatus.AwaitingPayment
    else -> WorkOrderStatus.Unsupported(this)
}

private fun JobRequestRequesterDto.fullName(): String = "$name $surname".trim()

private fun WorkOrderCounterpartDto.fullName(): String = "$name $surname".trim()
