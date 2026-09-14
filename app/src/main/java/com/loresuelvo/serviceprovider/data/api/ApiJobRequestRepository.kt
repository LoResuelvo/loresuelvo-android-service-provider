package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.mapper.toDomain
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.api.ApiError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class ApiJobRequestRepository @Inject constructor(
    private val backendApi: BackendApi,
) : JobRequestRepository {

    override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> = try {
        ActivityLoadOutcome.Success(backendApi.getJobRequests().map { it.toDomain() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        when (val error = e.toApiError()) {
            is ApiError.Network -> ActivityLoadOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized -> ActivityLoadOutcome.Failure.Unauthorized
            is ApiError.Server -> ActivityLoadOutcome.Failure.Server(error.code)
            is ApiError.Unknown -> ActivityLoadOutcome.Failure.Invalid
        }
    }

    override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome = try {
        val response = backendApi.acceptJobRequest(id)
        if (response.status != ACCEPTED_STATUS || response.conversationId <= 0) {
            AcceptJobRequestOutcome.Failure.Invalid
        } else {
            AcceptJobRequestOutcome.Success(
                requestId = response.id,
                conversationId = response.conversationId,
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        when (val error = e.toApiError()) {
            is ApiError.Network -> AcceptJobRequestOutcome.Failure.Network(error.networkCause)
            is ApiError.Unauthorized -> AcceptJobRequestOutcome.Failure.Unauthorized
            is ApiError.Server -> when (error.code) {
                400 -> AcceptJobRequestOutcome.Failure.Invalid
                403 -> AcceptJobRequestOutcome.Failure.Forbidden
                404 -> AcceptJobRequestOutcome.Failure.NotFound
                409 -> AcceptJobRequestOutcome.Failure.Conflict
                else -> AcceptJobRequestOutcome.Failure.Server(error.code)
            }
            is ApiError.Unknown -> AcceptJobRequestOutcome.Failure.Invalid
        }
    }

    private companion object {
        const val ACCEPTED_STATUS = "accepted"
    }
}
