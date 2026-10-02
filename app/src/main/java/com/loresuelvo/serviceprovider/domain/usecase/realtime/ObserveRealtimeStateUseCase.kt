package com.loresuelvo.serviceprovider.domain.usecase.realtime

import com.loresuelvo.serviceprovider.domain.realtime.RealtimeClient
import javax.inject.Inject

class ObserveRealtimeStateUseCase @Inject constructor(private val client: RealtimeClient) {
    operator fun invoke() = client.state
}
