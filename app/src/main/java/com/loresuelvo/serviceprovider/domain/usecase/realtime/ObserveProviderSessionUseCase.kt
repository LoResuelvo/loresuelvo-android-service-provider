package com.loresuelvo.serviceprovider.domain.usecase.realtime

import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import javax.inject.Inject

class ObserveProviderSessionUseCase @Inject constructor(private val sessions: AuthSessionStore) {
    operator fun invoke() = sessions.sessionFlow
}
