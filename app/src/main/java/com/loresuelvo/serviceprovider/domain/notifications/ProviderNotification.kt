package com.loresuelvo.serviceprovider.domain.notifications

data class ProviderNotification(
    val eventId: String,
    val destination: NotificationDestination,
    val recipientId: Int,
    val installationId: String,
    val bindingId: String,
    val title: String,
    val body: String,
    val expiresAt: Long,
)

data class NotificationBinding(
    val id: String,
    val subject: String,
    val recipientId: Int,
    val active: Boolean,
    val acknowledged: Boolean = false,
    val sessionKey: String? = null,
)

data class HandledNotification(
    val eventId: String,
    val bindingId: String,
    val destination: NotificationDestination,
    val expiresAt: Long,
    val tapId: String?,
)

data class NotificationInstallation(
    val id: String,
    val secret: String,
    val binding: NotificationBinding? = null,
    val previousBindingId: String? = null,
    val handled: List<HandledNotification> = emptyList(),
    val permissionRequested: Boolean = false,
    val acknowledgedBindingId: String? = null,
    val attemptedBindingIds: List<String> = emptyList(),
    val registrationToken: String? = null,
    val registrationLocale: String? = null,
    val registrationRejected: Boolean = false,
    val establishedSessionKey: String? = null,
)

sealed interface NotificationDestination {
    val id: Int
    data class Conversation(override val id: Int) : NotificationDestination
    data class WorkOrder(override val id: Int) : NotificationDestination
}

interface NotificationStateStore {
    fun read(): NotificationInstallation
    /** False means receipt must fail closed rather than risk replay after process death. */
    fun write(state: NotificationInstallation): Boolean
}

interface NotificationDisplay {
    fun canPost(): Boolean
    fun post(notice: ProviderNotification, tapId: String): Boolean
    fun cancelAll()
}

interface NotificationTokenSource {
    suspend fun token(): String?
}

fun interface NotificationClock {
    fun nowMillis(): Long
}

/** Shared by explicit logout, expiration, and account replacement before auth storage changes. */
fun interface NotificationSessionCleanup {
    fun invalidate()
    fun establish(session: com.loresuelvo.serviceprovider.domain.auth.AuthSession) = Unit
}
