package com.loresuelvo.serviceprovider.domain.usecase.notifications

import com.loresuelvo.serviceprovider.domain.notifications.*
import javax.inject.Inject

class RequestNotificationPermissionUseCase @Inject constructor(
    private val store: NotificationStateStore,
) {
    operator fun invoke(): Boolean = synchronized(store) {
        val state = store.read()
        !state.permissionRequested && store.write(state.copy(permissionRequested = true))
    }
}
