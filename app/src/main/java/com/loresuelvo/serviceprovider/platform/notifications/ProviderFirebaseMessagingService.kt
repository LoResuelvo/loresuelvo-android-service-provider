package com.loresuelvo.serviceprovider.platform.notifications

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.loresuelvo.serviceprovider.domain.notifications.NotificationLocalSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.usecase.notifications.RegisterNotificationInstallationUseCase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.loresuelvo.serviceprovider.data.api.mapper.ProviderNotificationParser
import com.loresuelvo.serviceprovider.domain.usecase.notifications.ReceiveProviderNotificationUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

@AndroidEntryPoint
class ProviderFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var receiveNotification: ReceiveProviderNotificationUseCase
    @Inject lateinit var registerInstallation: RegisterNotificationInstallationUseCase
    @Inject lateinit var sessions: AuthSessionStore
    @Inject lateinit var local: NotificationLocalSession
    private val registrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        ProviderNotificationParser().parse(message.data)?.let { receiveNotification(it) }
    }

    override fun onNewToken(token: String) {
        val account = local.accountFor(sessions.getSession()) ?: return
        val locale = resources.configuration.locales[0].language
        registrationScope.launch { registerInstallation(account, locale) }
    }

    override fun onDestroy() {
        registrationScope.cancel()
        super.onDestroy()
    }
}
