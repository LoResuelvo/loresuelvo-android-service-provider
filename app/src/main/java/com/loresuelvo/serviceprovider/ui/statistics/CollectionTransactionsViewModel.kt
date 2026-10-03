package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetCollectionTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CollectionTransactionsUiState(val purpose: CollectionPurpose? = null,
    val page: CollectionTransactions? = null, val loading: Boolean = false,
    val failure: CollectionsOutcome.Failure? = null, val restartRequired: Boolean = false,
    val sessionExpired: Boolean = false)

@HiltViewModel
class CollectionTransactionsViewModel @Inject constructor(
    private val getTransactions: GetCollectionTransactionsUseCase,
    private val sessionStore: AuthSessionStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CollectionTransactionsUiState())
    val uiState = mutableState.asStateFlow()
    private var query: CollectionTransactionsQuery? = null
    private var job: Job? = null
    private var requestId = 0L
    private var activeSession = sessionStore.getSession()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    invalidate()
                    mutableState.value = CollectionTransactionsUiState(sessionExpired = true)
                }
            }
        }
    }

    fun selectPeriod(period: ActivityPeriod) {
        val selected = CollectionTransactionsQuery(period.from, period.to, mutableState.value.purpose)
        if (query == selected) return
        query = selected
        invalidate()
        mutableState.value = CollectionTransactionsUiState(purpose = selected.purpose)
        retry()
    }

    fun selectPurpose(purpose: CollectionPurpose?) {
        val current = query ?: return
        if (purpose == current.purpose) return
        query = current.copy(purpose = purpose, cursor = null)
        invalidate()
        mutableState.value = CollectionTransactionsUiState(purpose = purpose)
        retry()
    }

    fun loadMore() {
        val state = mutableState.value
        if (state.loading || state.failure != null || state.sessionExpired) return
        val cursor = state.page?.nextCursor ?: return
        request(cursor)
    }

    fun retry() {
        val state = mutableState.value
        if (state.loading || state.sessionExpired) return
        request(if (state.restartRequired) null else state.page?.nextCursor)
    }

    private fun invalidate() { requestId++; job?.cancel(); job = null }

    private fun request(cursor: String?) {
        if (job?.isActive == true) return
        val requested = query?.copy(cursor = cursor) ?: return
        val session = sessionStore.getSession()
        if (session == null) {
            mutableState.value = CollectionTransactionsUiState(sessionExpired = true)
            return
        }
        val id = ++requestId
        mutableState.value = mutableState.value.copy(loading = true, failure = null)
        job = viewModelScope.launch {
            val outcome = getTransactions(requested)
            if (id != requestId || sessionStore.getSession() != session) return@launch
            when (outcome) {
                is CollectionTransactionsOutcome.Success -> {
                    val previous = if (cursor == null) emptyList() else mutableState.value.page?.transactions.orEmpty()
                    val rows = (previous + outcome.page.transactions).distinctBy { it.id }
                        .sortedWith(compareByDescending<CollectionTransaction> { it.verifiedOn }.thenByDescending { it.id })
                    mutableState.value = CollectionTransactionsUiState(requested.purpose,
                        outcome.page.copy(transactions = rows))
                }
                is CollectionTransactionsOutcome.Failure -> {
                    if (outcome.reason == CollectionsOutcome.Failure.Unauthorized) {
                        sessionStore.clearSession()
                        mutableState.value = CollectionTransactionsUiState(sessionExpired = true)
                    } else {
                        mutableState.value = mutableState.value.copy(loading = false, failure = outcome.reason,
                            restartRequired = cursor != null && outcome.reason == CollectionsOutcome.Failure.InvalidQuery)
                    }
                }
            }
        }
    }
}
