package com.loresuelvo.serviceprovider.domain.usecase.realtime

import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.realtime.RealtimeClient
import javax.inject.Inject
import kotlinx.coroutines.flow.filter

class ObserveProviderEventsUseCase @Inject constructor(
    private val client: RealtimeClient,
    private val sessions: AuthSessionStore,
) {
    operator fun invoke() = client.events.filter { it.session == sessions.getSession() }
}
