package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderConversionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderConversionUiState {
    data object Loading : ProviderConversionUiState
    data class Ready(val conversion: ProviderConversion) : ProviderConversionUiState
    data class Error(val failure: ConversionOutcome.Failure) : ProviderConversionUiState
    data object SessionExpired : ProviderConversionUiState
}

@HiltViewModel
class ProviderConversionViewModel @Inject constructor(
    private val getConversion: GetProviderConversionUseCase,
    private val sessionStore: AuthSessionStore,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow<ProviderConversionUiState>(ProviderConversionUiState.Loading)
    val uiState = mutableState.asStateFlow()
    val advancesExpanded = savedState.getStateFlow("conversion.expanded", false)
    val periodExpanded = savedState.getStateFlow("conversion.periodExpanded", false)
    private val mutableFilters = MutableStateFlow(ConversionFilters(savedState["conversion.fromDay"] ?: "",
        savedState["conversion.throughDay"] ?: ""))
    val filters = mutableFilters.asStateFlow()
    var query = ConversionQuery(savedState.get<String>("conversion.from")?.let(OffsetDateTime::parse),
        savedState.get<String>("conversion.to")?.let(OffsetDateTime::parse))
        private set
    private var opened = false
    private var loadJob: Job? = null
    private var requestId = 0L
    private var activeSession = sessionStore.getSession()
    val readingVersion get() = savedState.get<Long>("conversion.readingVersion") ?: 0L
    val readingIndex get() = savedState.get<Int>("conversion.readingIndex") ?: 0
    val readingOffset get() = savedState.get<Int>("conversion.readingOffset") ?: 0

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) expire()
            }
        }
    }
    private fun expire() {
        requestId++
        loadJob?.cancel()
        savedState["conversion.expanded"] = false
        savedState["conversion.periodExpanded"] = false
        savedState.keys().filter { it.startsWith("conversion.") && it !in setOf("conversion.expanded", "conversion.periodExpanded") }
            .forEach { savedState.remove<Any>(it) }
        query = ConversionQuery()
        mutableFilters.value = ConversionFilters()
        mutableState.value = ProviderConversionUiState.SessionExpired
    }
    fun expandAdvances(expanded: Boolean) { if (mutableState.value != ProviderConversionUiState.SessionExpired) savedState["conversion.expanded"] = expanded }
    fun expandPeriod(expanded: Boolean) { if (mutableState.value != ProviderConversionUiState.SessionExpired) savedState["conversion.periodExpanded"] = expanded }
    fun rememberReadingPosition(index: Int, offset: Int, version: Long) {
        if (version != readingVersion || mutableState.value !is ProviderConversionUiState.Ready) return
        savedState["conversion.readingIndex"] = index.coerceAtLeast(0)
        savedState["conversion.readingOffset"] = offset.coerceAtLeast(0)
    }
    fun editDates(fromDay: String, throughDay: String) {
        if (mutableState.value == ProviderConversionUiState.SessionExpired) return
        mutableFilters.value = ConversionFilters(fromDay, throughDay)
        savedState["conversion.fromDay"] = fromDay
        savedState["conversion.throughDay"] = throughDay
    }
    fun applyDates() {
        if (mutableState.value == ProviderConversionUiState.SessionExpired) return
        val (next, error) = conversionDates(filters.value, clock.instant())
        mutableFilters.value = filters.value.copy(dateError = error)
        if (error != null || next == null || next == query) return
        query = next
        persistQuery()
        savedState["conversion.readingVersion"] = readingVersion + 1
        savedState["conversion.readingIndex"] = 0
        savedState["conversion.readingOffset"] = 0
        request()
    }
    fun open() {
        if (opened || mutableState.value == ProviderConversionUiState.SessionExpired) return
        opened = true
        if (query.from == null || query.to == null) {
            val end = clock.instant()
            query = ConversionQuery(end.minus(Duration.ofDays(30)).atOffset(ZoneOffset.UTC), end.atOffset(ZoneOffset.UTC))
            persistQuery()
            editDates(query.from!!.atZoneSameInstant(conversionZone).toLocalDate().toString(),
                query.to!!.atZoneSameInstant(conversionZone).toLocalDate().toString())
        }
        request()
    }
    private fun persistQuery() {
        savedState["conversion.from"] = query.from.toString()
        savedState["conversion.to"] = query.to.toString()
    }
    fun retry() {
        if (!opened || loadJob?.isActive == true || mutableState.value == ProviderConversionUiState.SessionExpired) return
        request()
    }
    private fun request() {
        val session = sessionStore.getSession()
        if (session == null || session != activeSession || mutableState.value == ProviderConversionUiState.SessionExpired) {
            expire()
            return
        }
        val id = ++requestId
        loadJob?.cancel()
        val requested = query
        mutableState.value = ProviderConversionUiState.Loading
        loadJob = viewModelScope.launch {
            val outcome = getConversion(requested)
            if (id != requestId || sessionStore.getSession() != session) return@launch
            mutableState.value = when (outcome) {
                is ConversionOutcome.Success -> ProviderConversionUiState.Ready(outcome.conversion)
                ConversionOutcome.Failure.Unauthorized -> {
                    expire()
                    sessionStore.clearSession()
                    ProviderConversionUiState.SessionExpired
                }
                is ConversionOutcome.Failure -> ProviderConversionUiState.Error(outcome)
            }
        }
    }
}
