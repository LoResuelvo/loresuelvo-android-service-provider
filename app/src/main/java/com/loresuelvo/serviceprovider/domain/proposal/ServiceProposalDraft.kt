package com.loresuelvo.serviceprovider.domain.proposal

// Cohesion exception: these seven values are the complete editable validation input.
data class ServiceProposalDraft(
    val consumerId: Int,
    val amount: String,
    val date: String,
    val time: String,
    val reason: String,
    val duration: String,
    val selectedOffsetMinutes: Int? = null,
)

// Cohesion exception: these six values form one validated service proposal.
data class ValidatedServiceProposal(
    val consumerId: Int,
    val amountPesos: String,
    val scheduledEpochMillis: Long,
    val offsetMinutes: Int,
    val reason: String,
    val durationMinutes: Int,
)

sealed interface ProposalValidationError {
    data object Consumer : ProposalValidationError
    data object Amount : ProposalValidationError
    data object Date : ProposalValidationError
    data object Time : ProposalValidationError
    data object Reason : ProposalValidationError
    data object Duration : ProposalValidationError
    data object LeadTime : ProposalValidationError
    data class AmbiguousTime(val offsetsMinutes: List<Int>) : ProposalValidationError
}

sealed interface ProposalValidationOutcome {
    data class Valid(val proposal: ValidatedServiceProposal) : ProposalValidationOutcome
    data class Invalid(val errors: Set<ProposalValidationError>) : ProposalValidationOutcome
}
