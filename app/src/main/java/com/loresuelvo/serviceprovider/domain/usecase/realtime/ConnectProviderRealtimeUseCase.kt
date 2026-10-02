package com.loresuelvo.serviceprovider.domain.usecase.realtime

import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.realtime.RealtimeClient
import javax.inject.Inject

class ConnectProviderRealtimeUseCase @Inject constructor(private val client: RealtimeClient) {
    suspend operator fun invoke(session: AuthSession) = client.connect(session)
}
