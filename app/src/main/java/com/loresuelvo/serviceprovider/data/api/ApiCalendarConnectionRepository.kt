package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.ConnectCalendarRequestDto
import com.loresuelvo.serviceprovider.domain.api.ApiError
import com.loresuelvo.serviceprovider.domain.calendar.CalendarConnectionRepository
import com.loresuelvo.serviceprovider.domain.calendar.ConnectCalendarOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class ApiCalendarConnectionRepository @Inject constructor(
    private val api: BackendApi,
) : CalendarConnectionRepository {
    override suspend fun connect(serverAuthCode: String, session: com.loresuelvo.serviceprovider.domain.auth.AuthSession): ConnectCalendarOutcome {
        if (serverAuthCode.isBlank()) return ConnectCalendarOutcome.Rejected
        return try {
            val response = api.connectCalendar(ConnectCalendarRequestDto(serverAuthCode), session)
            response.errorBody()?.close()
            when (response.code()) {
                201 -> ConnectCalendarOutcome.Submitted
                401 -> ConnectCalendarOutcome.Unauthorized
                in 400..499 -> ConnectCalendarOutcome.Rejected
                else -> ConnectCalendarOutcome.Unavailable
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            when (error.toApiError()) {
                is ApiError.Unauthorized -> ConnectCalendarOutcome.Unauthorized
                else -> ConnectCalendarOutcome.Unavailable
            }
        }
    }
}
