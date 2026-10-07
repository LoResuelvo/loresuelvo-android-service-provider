package com.loresuelvo.serviceprovider.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.usecase.notifications.RegisterNotificationInstallationUseCase
import com.loresuelvo.serviceprovider.domain.usecase.notifications.RequestNotificationPermissionUseCase
import com.loresuelvo.serviceprovider.domain.usecase.notifications.ConsumeNotificationTargetUseCase
import com.loresuelvo.serviceprovider.domain.notifications.NotificationLocalSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@HiltViewModel
class ProviderNotificationViewModel @Inject constructor(
    private val register: RegisterNotificationInstallationUseCase,
    private val permission: RequestNotificationPermissionUseCase,
    private val consume: ConsumeNotificationTargetUseCase,
    local: NotificationLocalSession,
) : ViewModel() {
    val target = local.target
    private val pendingEffects = Channel<NotificationUiEffect>(Channel.BUFFERED)
    val effects = pendingEffects.receiveAsFlow()
    fun openSettings() { pendingEffects.trySend(NotificationUiEffect.OpenSettings) }
    fun enter(recipientId: Int, locale: String) { viewModelScope.launch { register(recipientId, locale) } }
    fun requestPermissionOnce(): Boolean = permission()
    fun consumeTarget(): Int? = consume()
}

enum class NotificationUiEffect { OpenSettings }
