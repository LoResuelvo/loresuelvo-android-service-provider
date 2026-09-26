package com.loresuelvo.serviceprovider.domain.proposal


enum class ServiceProposalStatus { Pending, Accepted, Rejected }

data class ServiceProposalCounterpart(
    val id: Int,
    val role: String,
    val name: String,
    val surname: String,
    val categoryName: String?,
    val profilePhotoUrl: String?,
)

data class ServiceProposalBookingTerms(
    val currency: String,
    val serviceTotalCents: Long,
    val depositCents: Long,
    val remainingServiceBalanceCents: Long,
    val platformFeeTotalCents: Long,
    val platformFeeDueNowCents: Long,
    val remainingPlatformFeeCents: Long,
    val amountDueNowCents: Long,
    val remainingAmountDueCents: Long,
    val contractTotalCents: Long,
    val bookingPaymentDeadlineEpochMillis: Long,
)

data class ServiceProposalSummary(
    val id: Int,
    val conversationId: Int,
    val amountCents: Long,
    val scheduledOnEpochMillis: Long,
    val description: String,
    val estimatedDurationMinutes: Int,
    val status: ServiceProposalStatus,
    val createdOnEpochMillis: Long,
    val counterpart: ServiceProposalCounterpart,
    val bookingTerms: ServiceProposalBookingTerms,
)

sealed interface ServiceProposalListOutcome {
    data class Success(val proposals: List<ServiceProposalSummary>) : ServiceProposalListOutcome
    sealed interface Failure : ServiceProposalListOutcome {
        data object SessionExpired : Failure
        data object Unavailable : Failure
        data object InvalidResponse : Failure
    }
}
