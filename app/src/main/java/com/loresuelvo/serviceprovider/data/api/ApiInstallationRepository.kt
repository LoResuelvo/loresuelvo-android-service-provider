package com.loresuelvo.serviceprovider.data.api

import com.loresuelvo.serviceprovider.data.api.dto.InstallationRequestDto
import com.loresuelvo.serviceprovider.data.api.dto.RemoveInstallationRequestDto
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.notifications.InstallationRepository
import com.loresuelvo.serviceprovider.domain.notifications.InstallationResult
import com.loresuelvo.serviceprovider.domain.notifications.NotificationInstallation
import java.io.IOException
import javax.inject.Inject

class ApiInstallationRepository @Inject constructor(private val api: BackendApi) : InstallationRepository {
    override suspend fun register(installation: NotificationInstallation, token: String, locale: String, session: AuthSession): InstallationResult {
        val binding = installation.binding ?: return InstallationResult.Invalid
        return execute(setOf(200, 201)) {
            api.registerInstallation(installation.id, InstallationRequestDto(installation.secret, app = "provider", token = token,
                locale = locale, bindingId = binding.id, previousBindingId = installation.previousBindingId), session).code()
        }
    }

    override suspend fun remove(installation: NotificationInstallation, session: AuthSession): InstallationResult {
        val binding = installation.binding ?: return InstallationResult.Invalid
        return execute(setOf(204)) {
            api.removeInstallation(installation.id, RemoveInstallationRequestDto(installation.secret, binding.id), session).code()
        }
    }

    private suspend fun execute(acceptedStatuses: Set<Int>, request: suspend () -> Int): InstallationResult = try {
        when (request()) {
            in acceptedStatuses -> InstallationResult.Applied
            401 -> InstallationResult.Unauthorized
            403 -> InstallationResult.Forbidden
            409 -> InstallationResult.Conflict
            in 500..599 -> InstallationResult.TransientFailure
            else -> InstallationResult.Invalid
        }
    } catch (_: IOException) {
        InstallationResult.TransientFailure
    }
}
