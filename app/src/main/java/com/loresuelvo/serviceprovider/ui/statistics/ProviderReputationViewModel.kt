package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.statistics.ProviderReputation
import com.loresuelvo.serviceprovider.domain.statistics.ReputationOutcome
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderReputationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderReputationUiState {
    data object Loading : ProviderReputationUiState
    data class Ready(val reputation: ProviderReputation, val loading: Boolean = false,
        val failure: ReputationOutcome.Failure? = null, val restartRequired: Boolean = false,
        val restoring: Boolean = false, val readingVersion: Long = 0) : ProviderReputationUiState
    data class Error(val failure: ReputationOutcome.Failure) : ProviderReputationUiState
    data object SessionExpired : ProviderReputationUiState
}

@HiltViewModel
class ProviderReputationViewModel @Inject constructor(
    private val getReputation: GetProviderReputationUseCase,
    private val sessionStore: AuthSessionStore,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProviderReputationUiState>(ProviderReputationUiState.Loading)
    val uiState = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var requestId = 0L
    private var activeSession = sessionStore.getSession()
    private var opened = false
    private var failedCursor: String? = null
    private var resetOnSuccess = false
    private var loadedPages = 0
    private var restorePages = savedState.get<Int>("reputation.pages")?.coerceAtLeast(1) ?: 1
    private var readingVersion = 0L
    val readingIndex: Int get() = savedState["reputation.index"] ?: 0
    val readingOffset: Int get() = savedState["reputation.offset"] ?: 0

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    invalidate()
                    failedCursor = null
                    loadedPages = 0
                    restorePages = 1
                    resetOnSuccess = false
                    rememberReadingPosition(0, 0)
                    savedState["reputation.pages"] = 1
                    mutableState.value = ProviderReputationUiState.SessionExpired
                }
            }
        }
    }
    fun rememberReadingPosition(index: Int, offset: Int) {
        savedState["reputation.index"] = index.coerceAtLeast(0)
        savedState["reputation.offset"] = offset.coerceAtLeast(0)
    }
    fun open() {
        if (opened) return
        opened = true
        request(null)
    }
    fun loadMore() {
        val ready = mutableState.value as? ProviderReputationUiState.Ready ?: return
        if (ready.loading || ready.failure != null || ready.restoring) return
        request(ready.reputation.nextCursor ?: return)
    }
    fun retry() {
        if (loadJob?.isActive == true || mutableState.value == ProviderReputationUiState.SessionExpired) return
        val ready = mutableState.value as? ProviderReputationUiState.Ready
        if (ready?.restartRequired == true) refresh()
        else request(if (ready?.failure != null) failedCursor else null)
    }
    fun refresh() {
        if (mutableState.value == ProviderReputationUiState.SessionExpired) return
        invalidate()
        restorePages = 1
        resetOnSuccess = true
        request(null)
    }
    private fun invalidate() { requestId++; loadJob?.cancel(); loadJob = null }

    private fun request(cursor: String?) {
        if (loadJob?.isActive == true) return
        val session = sessionStore.getSession()
        if (session == null || session != activeSession) {
            invalidate()
            mutableState.value = ProviderReputationUiState.SessionExpired
            return
        }
        val id = ++requestId
        val previous = mutableState.value as? ProviderReputationUiState.Ready
        mutableState.value = previous?.copy(loading = true, failure = null)
            ?: ProviderReputationUiState.Loading
        loadJob = viewModelScope.launch {
            var next = cursor
            do {
                val outcome = getReputation(next)
                if (id != requestId || sessionStore.getSession() != session) return@launch
                when (outcome) {
                    is ReputationOutcome.Success -> {
                        next = acceptPage(outcome.reputation, next)
                        if (next == null) break
                    }
                    ReputationOutcome.Failure.Unauthorized -> {
                        sessionStore.clearSession()
                        rememberReadingPosition(0, 0)
                        savedState["reputation.pages"] = 1
                        mutableState.value = ProviderReputationUiState.SessionExpired
                        break
                    }
                    is ReputationOutcome.Failure -> {
                        failedCursor = next
                        val ready = mutableState.value as? ProviderReputationUiState.Ready
                        mutableState.value = ready?.copy(loading = false, failure = outcome,
                            restartRequired = next != null && outcome == ReputationOutcome.Failure.InvalidQuery)
                            ?: ProviderReputationUiState.Error(outcome)
                        break
                    }
                }
            } while (true)
        }
    }
    private fun acceptPage(page: ProviderReputation, cursor: String?): String? {
        val prior = (mutableState.value as? ProviderReputationUiState.Ready)?.reputation
        val rows = (if (cursor == null) emptyList() else prior?.reviews.orEmpty()) + page.reviews
        loadedPages = if (cursor == null) 1 else loadedPages + 1
        val restoring = loadedPages < restorePages && page.nextCursor != null
        if (resetOnSuccess && cursor == null) {
            rememberReadingPosition(0, 0)
            readingVersion++
            resetOnSuccess = false
        }
        // Only metadata survives process death; each restored page is fetched again.
        if (!restoring) savedState["reputation.pages"] = loadedPages
        mutableState.value = ProviderReputationUiState.Ready(page.copy(
            reviews = rows.distinctBy { it.workOrderId }.sortedByDescending { it.workOrderId }),
            loading = restoring, restoring = restoring, readingVersion = readingVersion)
        failedCursor = null
        return if (restoring) page.nextCursor else null
    }

}
