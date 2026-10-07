package com.loresuelvo.serviceprovider.domain.notifications

import com.loresuelvo.serviceprovider.domain.auth.AuthSession

interface InstallationRepository {
    suspend fun register(installation: NotificationInstallation, token: String, locale: String, session: AuthSession): InstallationResult
    suspend fun remove(installation: NotificationInstallation, session: AuthSession): InstallationResult
}

sealed interface InstallationResult {
    data object Applied : InstallationResult
    data object Unauthorized : InstallationResult
    data object Forbidden : InstallationResult
    data object Conflict : InstallationResult
    data object Invalid : InstallationResult
    data object TransientFailure : InstallationResult
}
