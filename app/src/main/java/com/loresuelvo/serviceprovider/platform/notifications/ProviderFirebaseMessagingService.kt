package com.loresuelvo.serviceprovider.platform.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.loresuelvo.serviceprovider.data.api.mapper.ProviderNotificationParser
import com.loresuelvo.serviceprovider.domain.usecase.notifications.ReceiveProviderNotificationUseCase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProviderFirebaseMessagingService : FirebaseMessagingService() {
    @Inject lateinit var receiveNotification: ReceiveProviderNotificationUseCase

    override fun onMessageReceived(message: RemoteMessage) {
        ProviderNotificationParser().parse(message.data)?.let { receiveNotification(it) }
    }
}
