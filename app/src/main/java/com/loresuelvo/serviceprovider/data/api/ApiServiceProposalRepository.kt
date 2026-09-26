package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ServiceProposalErrorDto
import com.loresuelvo.serviceprovider.data.api.mapper.isConfirmedPendingFor
import com.loresuelvo.serviceprovider.data.api.mapper.toRequestDto
import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.proposal.CreateServiceProposalOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalRepository
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalListOutcome
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiServiceProposalRepository @Inject constructor(
    private val api: ServiceProposalApi,
    private val json: Json,
) : ServiceProposalRepository {
    override suspend fun list(): ServiceProposalListOutcome = try {
        val response = api.list()
        when {
            response.code() == 401 -> ServiceProposalListOutcome.Failure.SessionExpired
            !response.isSuccessful -> ServiceProposalListOutcome.Failure.Unavailable
            else -> response.body()?.let { items ->
                ServiceProposalListOutcome.Success(items.map { it.toDomain() })
            } ?: ServiceProposalListOutcome.Failure.InvalidResponse
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: SerializationException) {
        ServiceProposalListOutcome.Failure.InvalidResponse
    } catch (_: IllegalArgumentException) {
        ServiceProposalListOutcome.Failure.InvalidResponse
    } catch (_: Exception) {
        ServiceProposalListOutcome.Failure.Unavailable
    }

    override suspend fun create(proposal: ValidatedServiceProposal): CreateServiceProposalOutcome = try {
        mapResponse(api.create(proposal.toRequestDto()), proposal)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        CreateServiceProposalOutcome.Failure.Uncertain
    }

    private fun mapResponse(
        response: Response<com.loresuelvo.serviceprovider.data.api.dto.ServiceProposalResponseDto>,
        proposal: ValidatedServiceProposal,
    ): CreateServiceProposalOutcome {
        if (response.code() == 201) {
            val body = response.body() ?: return CreateServiceProposalOutcome.Failure.Uncertain
            return if (body.isConfirmedPendingFor(proposal)) CreateServiceProposalOutcome.Created(body.id)
            else CreateServiceProposalOutcome.Failure.Uncertain
        }
        if (response.isSuccessful || response.code() == 408 || response.code() >= 500) {
            return CreateServiceProposalOutcome.Failure.Uncertain
        }
        val message = response.errorBody()?.string()?.let {
            runCatching { json.decodeFromString<ServiceProposalErrorDto>(it).error }.getOrNull()
        }
        return when (response.code()) {
            400 -> when (message) {
                "Amount must be greater than 0", "amount must be a positive decimal with at most two decimal places" ->
                    CreateServiceProposalOutcome.Failure.Invalid(setOf(ProposalValidationError.Amount))
                "Estimated duration is required", "Estimated duration must be between 15 and 1440 minutes" ->
                    CreateServiceProposalOutcome.Failure.Invalid(setOf(ProposalValidationError.Duration))
                "Scheduled on must be in the future", "Scheduled on must be more than 24 hours in the future" ->
                    CreateServiceProposalOutcome.Failure.Invalid(setOf(ProposalValidationError.LeadTime))
                else -> CreateServiceProposalOutcome.Failure.Rejected
            }
            401 -> CreateServiceProposalOutcome.Failure.SessionExpired
            403 -> CreateServiceProposalOutcome.Failure.ProviderIneligible
            404 -> CreateServiceProposalOutcome.Failure.ConsumerUnavailable
            409 -> when (message) {
                "A connected payment account is required before creating a service proposal" ->
                    CreateServiceProposalOutcome.Failure.PaymentRequired
                "Conversation is not active",
                "Provider and consumer must have an active conversation before creating a service proposal" ->
                    CreateServiceProposalOutcome.Failure.InactiveConversation
                else -> CreateServiceProposalOutcome.Failure.Conflict
            }
            else -> CreateServiceProposalOutcome.Failure.Uncertain
        }
    }
}
