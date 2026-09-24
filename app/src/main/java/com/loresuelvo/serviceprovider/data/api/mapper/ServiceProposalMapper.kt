package com.loresuelvo.serviceprovider.data.api.mapper

import com.loresuelvo.serviceprovider.data.api.dto.CreateServiceProposalRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.ServiceProposalResponseDto
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
