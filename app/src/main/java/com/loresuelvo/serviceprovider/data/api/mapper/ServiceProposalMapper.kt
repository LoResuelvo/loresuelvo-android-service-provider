package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.CreateServiceProposalRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.ServiceProposalResponseDto
import com.loresuelvo.serviceprovider.data.api.dto.ServiceProposalListItemDto
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun ValidatedServiceProposal.toRequestDto() = CreateServiceProposalRequestDto(
    consumerId = consumerId,
    amount = amountPesos,
    scheduledOn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(scheduledEpochMillis)),
    description = reason,
    estimatedDurationMinutes = durationMinutes,
)

internal fun ServiceProposalListItemDto.toDomain(): ServiceProposalSummary {
    require(id > 0 && conversationId > 0 && amountCents >= 1 && estimatedDurationMinutes in 15..1440)
    require(counterpart.id > 0 && counterpart.role == "consumer" && counterpart.name.isNotBlank())
    val typedStatus = when (status) {
        "pending" -> ServiceProposalStatus.Pending
        "accepted" -> ServiceProposalStatus.Accepted
        "rejected" -> ServiceProposalStatus.Rejected
        else -> throw IllegalArgumentException("Unsupported service proposal status")
    }
    return ServiceProposalSummary(
        id = id,
        conversationId = conversationId,
        amountCents = amountCents,
        scheduledOnEpochMillis = scheduledOn.toEpochMillis(),
        description = description,
        estimatedDurationMinutes = estimatedDurationMinutes,
        status = typedStatus,
        createdOnEpochMillis = createdOn.toEpochMillis(),
        counterpart = ServiceProposalCounterpart(
            counterpart.id, counterpart.role, counterpart.name, counterpart.surname,
            counterpart.categoryName, counterpart.profilePhotoUrl,
        ),
        bookingTerms = bookingTerms.let {
            ServiceProposalBookingTerms(
                it.currency, it.serviceTotalCents, it.depositCents, it.remainingServiceBalanceCents,
                it.platformFeeTotalCents, it.platformFeeDueNowCents, it.remainingPlatformFeeCents,
                it.amountDueNowCents, it.remainingAmountDueCents, it.contractTotalCents,
                it.bookingPaymentDeadline.toEpochMillis(),
            )
        },
    )
}

internal fun ServiceProposalResponseDto.isConfirmedPendingFor(proposal: ValidatedServiceProposal): Boolean =
    id > 0 && conversationId > 0 && consumerId == proposal.consumerId && providerId > 0 &&
        amountCents == BigDecimal(proposal.amountPesos).movePointRight(2).longValueExact() &&
        scheduledOn.toEpochMillis() == proposal.scheduledEpochMillis &&
        description == proposal.reason && status == "pending" &&
        estimatedDurationMinutes == proposal.durationMinutes &&
        bookingTerms.currency.isNotBlank() &&
        bookingTerms.serviceTotalCents >= 0 && bookingTerms.depositCents >= 0 &&
        bookingTerms.remainingServiceBalanceCents >= 0 &&
        bookingTerms.platformFeeTotalCents >= 0 &&
        bookingTerms.platformFeeDueNowCents >= 0 &&
        bookingTerms.remainingPlatformFeeCents >= 0 &&
        bookingTerms.amountDueNowCents >= 0 &&
        bookingTerms.remainingAmountDueCents >= 0 &&
        bookingTerms.contractTotalCents >= 0 &&
        bookingTerms.bookingPaymentDeadline.toEpochMillis() > 0
