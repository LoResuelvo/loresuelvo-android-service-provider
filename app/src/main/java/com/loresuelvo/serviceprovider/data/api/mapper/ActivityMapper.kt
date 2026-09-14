package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.JobRequestRequesterDto
import com.loresuelvo.serviceprovider.data.api.dto.JobRequestSummaryDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderCounterpartDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderSummaryDto
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

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

private fun String.toEpochMillis(): Long {
    val match = RFC3339_PATTERN.matchEntire(this)
        ?: throw IllegalArgumentException("Invalid scheduled_on")
    val fraction = match.groupValues[2]
        .padEnd(3, '0')
        .take(3)
    val offset = match.groupValues[3]
        .replace(":", "")
        .let { if (it == "Z") "+0000" else it }
    val normalized = "${match.groupValues[1]}.$fraction$offset"
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).apply {
        isLenient = false
    }
    val position = ParsePosition(0)
    val date = parser.parse(normalized, position)
    if (date == null || position.index != normalized.length) {
        throw IllegalArgumentException("Invalid scheduled_on")
    }
    return date.time
}

private val RFC3339_PATTERN = Regex("^(.*?)(?:\\.(\\d+))?(Z|[+-]\\d{2}:?\\d{2})$")
