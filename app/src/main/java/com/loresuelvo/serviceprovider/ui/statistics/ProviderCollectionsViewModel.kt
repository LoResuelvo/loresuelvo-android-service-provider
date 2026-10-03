package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.statistics.ActivityQuery
import com.loresuelvo.serviceprovider.domain.statistics.CollectionsOutcome
import com.loresuelvo.serviceprovider.domain.statistics.ProviderCollections
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderCollectionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderCollectionsUiState {
    data object Loading : ProviderCollectionsUiState
    data class Ready(val collections: ProviderCollections) : ProviderCollectionsUiState
    data class Error(val failure: CollectionsOutcome.Failure) : ProviderCollectionsUiState
    data object SessionExpired : ProviderCollectionsUiState
}

@HiltViewModel
class ProviderCollectionsViewModel @Inject constructor(
    private val getCollections: GetProviderCollectionsUseCase,
    private val sessionStore: AuthSessionStore,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProviderCollectionsUiState>(ProviderCollectionsUiState.Loading)
    val uiState = mutableState.asStateFlow()
    val evolutionExpanded = savedState.getStateFlow("collections.evolutionExpanded", false)
    fun expandEvolution(expanded: Boolean) { savedState["collections.evolutionExpanded"] = expanded }
    var query: ActivityQuery? = null
        private set
    private var requestId = 0L
    private var loadJob: Job? = null
    private var activeSession = sessionStore.getSession()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    requestId++
                    loadJob?.cancel()
                    mutableState.value = ProviderCollectionsUiState.SessionExpired
                }
            }
        }
    }

    fun selectQuery(selected: ActivityQuery) {
        if (query == selected) return
        query = selected
        requestId++
        loadJob?.cancel()
        loadJob = null
        retry()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        val requested = query ?: return
        val session = sessionStore.getSession()
        if (session == null) {
            mutableState.value = ProviderCollectionsUiState.SessionExpired
            return
        }
        val id = ++requestId
        mutableState.value = ProviderCollectionsUiState.Loading
        loadJob = viewModelScope.launch {
            val outcome = getCollections(requested)
            if (id != requestId || sessionStore.getSession() != session) return@launch
            mutableState.value = when (outcome) {
                is CollectionsOutcome.Success -> ProviderCollectionsUiState.Ready(outcome.collections)
                CollectionsOutcome.Failure.Unauthorized -> {
                    sessionStore.clearSession()
                    ProviderCollectionsUiState.SessionExpired
                }
                is CollectionsOutcome.Failure -> ProviderCollectionsUiState.Error(outcome)
            }
        }
    }
}
