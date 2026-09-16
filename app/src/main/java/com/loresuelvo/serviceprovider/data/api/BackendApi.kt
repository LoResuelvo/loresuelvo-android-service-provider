package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.CategoryDto
import com.loresuelvo.serviceprovider.data.api.dto.ConfirmFileRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationDetailDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationDto
import com.loresuelvo.serviceprovider.data.api.dto.ConversationMessageDto
import com.loresuelvo.serviceprovider.data.api.dto.CoverageZoneDto
import com.loresuelvo.serviceprovider.data.api.dto.CurrentAccountDto
import com.loresuelvo.serviceprovider.data.api.dto.FileResponseDto
import com.loresuelvo.serviceprovider.data.api.dto.JobRequestSummaryDto
import com.loresuelvo.serviceprovider.data.api.dto.PaymentAccountAuthorizationDto
import com.loresuelvo.serviceprovider.data.api.dto.PaymentAccountStatusDto
import com.loresuelvo.serviceprovider.data.api.dto.PresignFileRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.PresignFileResponseDto
import com.loresuelvo.serviceprovider.data.api.dto.ProviderSummaryDto
import com.loresuelvo.serviceprovider.data.api.dto.ProviderProfileDto
import com.loresuelvo.serviceprovider.data.api.dto.RegisterProviderRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.SendMessageRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.WorkOrderSummaryDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit-typed contract for the backend's provider endpoints. The
 * interface is intentionally narrow: only the calls this app needs.
 * Adding a new endpoint here is the only way to add a new network
 * operation; the use cases never see Retrofit types.
 *
 * Wire paths mirror `loresuelvo-api/internal/adapters/http/router.go`.
 */
interface BackendApi {

    @GET("me")
    suspend fun getCurrentAccount(): CurrentAccountDto

    @GET("job-requests")
    suspend fun getJobRequests(): List<JobRequestSummaryDto>

    @POST("job-requests/{jobRequestID}/accept")
    suspend fun acceptJobRequest(
        @Path("jobRequestID") jobRequestId: Int,
    ): JobRequestSummaryDto

    @GET("work-orders")
    suspend fun getWorkOrders(): List<WorkOrderSummaryDto>

    @GET("conversations")
    suspend fun getConversations(): List<ConversationDto>

    /**
     * `GET /conversations/{conversationId}` — full snapshot of a
     * single conversation including its complete ordered
     * `messages` thread. The chat surface uses it on entry to
     * render the header (counterpart, status) and the existing
     * bubbles. Empty `messages` is a valid response for a brand-
     * new conversation that was just opened by the consumer.
     *
     * 404 maps to [com.loresuelvo.serviceprovider.domain.conversation.ConversationDetailOutcome.Failure.NotFound]
     * at the repository layer.
     */
    @GET("conversations/{conversationId}")
    suspend fun getConversationById(
        @Path("conversationId") conversationId: Int,
    ): ConversationDetailDto

    /**
     * `POST /conversations/{conversationId}/messages` — appends a
     * provider-typed text message to the given conversation. The
     * response carries the server-persisted message (with the
     * backend-issued id and the authoritative `created_on`
     * timestamp) so the ViewModel can replace its optimistic
     * bubble without a follow-up `GET` round-trip.
     *
     * 404 maps to
     * [com.loresuelvo.serviceprovider.domain.conversation.SendMessageOutcome.Failure.ConversationNotFound]
     * at the repository layer.
     */
    @POST("conversations/{conversationId}/messages")
    suspend fun postMessage(
        @Path("conversationId") conversationId: Int,
        @Body request: SendMessageRequestDto,
    ): ConversationMessageDto

    /**
     * `GET /categories` — the platform's service categories. Public
     * (no auth required), so it can be called before the user signs
     * in. Returns a JSON array of [CategoryDto]; non-2xx throws
     * [retrofit2.HttpException], mapped by the data layer to
     * [com.loresuelvo.serviceprovider.domain.api.ApiError].
     */
    @GET("categories")
    suspend fun getCategories(): List<CategoryDto>

    @GET("coverage-zones")
    suspend fun getCoverageZones(): List<CoverageZoneDto>

    /**
     * `POST /providers` — creates the provider record on the platform.
     * Authenticated endpoint requiring session bearer token.
     */
    @POST("providers")
    suspend fun registerProvider(
        @Body request: RegisterProviderRequestDto,
    ): ProviderSummaryDto

    /**
     * `GET /providers/{providerID}` — retrieves public provider profile.
     */
    @GET("providers/{providerID}")
    suspend fun getProviderProfile(
        @Path("providerID") providerId: Int,
    ): ProviderProfileDto

    /**
     * `POST /files/presign` — requests a presigned storage upload URL.
     */
    @POST("files/presign")
    suspend fun presignFile(
        @Body request: PresignFileRequestDto,
    ): PresignFileResponseDto

    /**
     * `POST /files/{fileID}/confirm` — confirms an uploaded file with the backend.
     */
    @POST("files/{fileID}/confirm")
    suspend fun confirmFile(
        @Path("fileID") fileId: String,
        @Body request: ConfirmFileRequestDto,
    ): FileResponseDto

    @GET("providers/me/payment-accounts")
    suspend fun getPaymentAccountStatus(): PaymentAccountStatusDto

    @POST("providers/me/payment-accounts/authorization")
    suspend fun requestPaymentAccountAuthorization(): PaymentAccountAuthorizationDto
}
