package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.CollectionTransactionsDto
import com.loresuelvo.serviceprovider.domain.statistics.*
import java.time.OffsetDateTime

internal fun CollectionTransactionsDto.toDomain(): CollectionTransactions {
    val from = OffsetDateTime.parse(period.from).toInstant()
    val to = OffsetDateTime.parse(period.to).toInstant()
    require(from < to && period.timeZone == "America/Argentina/Buenos_Aires")
    require(currency == "ARS" && totalCount >= 0 && totalAmountCents >= 0)
    require(nextCursor == null || nextCursor.isNotBlank())
    val rows = transactions.map { row ->
        val verified = OffsetDateTime.parse(row.verifiedOn).toInstant()
        val purpose = when (row.purpose) {
            "booking_deposit" -> CollectionPurpose.BOOKING_DEPOSIT
            "service_balance" -> CollectionPurpose.SERVICE_BALANCE
            else -> throw IllegalArgumentException("Unsupported collection purpose")
        }
        require(row.id > 0 && row.sellerAmountCents >= 0 && row.currency == "ARS")
        require(row.serviceProposalId > 0 && (row.workOrderId == null || row.workOrderId > 0))
        require(verified >= from && verified < to)
        CollectionTransaction(row.id, verified, purpose, row.sellerAmountCents, row.currency,
            row.serviceProposalId, row.workOrderId)
    }
    require(rows.size.toLong() <= totalCount && rows.map { it.id }.distinct().size == rows.size)
    require(rows.zipWithNext().all { (a, b) -> a.verifiedOn > b.verifiedOn ||
        (a.verifiedOn == b.verifiedOn && a.id > b.id) })
    require(rows.fold(0L) { sum, row -> Math.addExact(sum, row.sellerAmountCents) } <= totalAmountCents)
    return CollectionTransactions(from, to, period.timeZone, OffsetDateTime.parse(calculatedAt).toInstant(),
        currency, totalCount, totalAmountCents, rows, nextCursor)
}
