package com.loresuelvo.serviceprovider.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CollectionTransactionsPeriodDto(val from: String, val to: String,
    @SerialName("time_zone") val timeZone: String)
@Serializable
data class CollectionTransactionsDto(val period: CollectionTransactionsPeriodDto,
    @SerialName("calculated_at") val calculatedAt: String, val currency: String,
    @SerialName("total_count") val totalCount: Long,
    @SerialName("total_amount_cents") val totalAmountCents: Long,
    val transactions: List<CollectionTransactionDto>, @SerialName("next_cursor") val nextCursor: String?)
@Serializable
data class CollectionTransactionDto(val id: Long, @SerialName("verified_on") val verifiedOn: String,
    val purpose: String, @SerialName("seller_amount_cents") val sellerAmountCents: Long,
    val currency: String, @SerialName("service_proposal_id") val serviceProposalId: Int,
    @SerialName("work_order_id") val workOrderId: Int?)
