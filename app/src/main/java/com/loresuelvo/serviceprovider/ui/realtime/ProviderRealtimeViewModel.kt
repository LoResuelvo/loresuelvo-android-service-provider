package com.loresuelvo.serviceprovider.ui.realtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.usecase.realtime.ObserveProviderSessionUseCase
import com.loresuelvo.serviceprovider.domain.usecase.realtime.ConnectProviderRealtimeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Activity-owned transport, shared by every destination in the authenticated foreground. */
@HiltViewModel
class ProviderRealtimeViewModel @Inject constructor(
    observeSession: ObserveProviderSessionUseCase,
    connect: ConnectProviderRealtimeUseCase,
) : ViewModel() {
    private val foreground = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combine(observeSession(), foreground) { session, active -> session.takeIf { active } }
                .collectLatest { session ->
                    if (session != null && session.accessToken.isNotBlank()) connect(session)
                }
        }
    }

    fun onForegroundChanged(active: Boolean) { foreground.value = active }
}
