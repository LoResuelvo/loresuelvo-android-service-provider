package com.loresuelvo.serviceprovider.domain.proposal

interface ServiceProposalRepository {
    suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome
}

sealed interface CreateServiceProposalOutcome {
    data class Created(val id: Int) : CreateServiceProposalOutcome
    sealed interface Failure : CreateServiceProposalOutcome {
        data class Invalid(val errors: Set<ProposalValidationError>) : Failure
        data object Rejected : Failure
        data object SessionExpired : Failure
        data object ProviderIneligible : Failure
        data object ConsumerUnavailable : Failure
        data object PaymentRequired : Failure
        data object InactiveConversation : Failure
        data object Conflict : Failure
        data object Uncertain : Failure
    }
}
