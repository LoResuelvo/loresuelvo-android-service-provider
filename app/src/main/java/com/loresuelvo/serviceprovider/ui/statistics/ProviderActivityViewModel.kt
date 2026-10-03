package com.loresuelvo.serviceprovider.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.statistics.*
import com.loresuelvo.serviceprovider.domain.usecase.statistics.GetProviderActivityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderActivityUiState {
    data object Loading : ProviderActivityUiState
    data class Ready(val activity: ProviderActivity) : ProviderActivityUiState
    data class Error(val failure: ActivityOutcome.Failure) : ProviderActivityUiState
    data object SessionExpired : ProviderActivityUiState
}

enum class ActivityDateError { FORMAT, REVERSED, TOO_LONG, FUTURE }
data class ActivityFilters(val fromDay: String, val throughDay: String, val query: ActivityQuery,
    val dateError: ActivityDateError? = null)

@HiltViewModel
class ProviderActivityViewModel @Inject constructor(
    private val getActivity: GetProviderActivityUseCase,
    private val sessionStore: AuthSessionStore,
    private val clock: Clock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val end = clock.instant()
    private val zone = ZoneId.of("America/Argentina/Buenos_Aires")
    var query = ActivityQuery(
        savedState.get<String>("activity.from")?.let(java.time.Instant::parse) ?: end.minus(Duration.ofDays(30)),
        savedState.get<String>("activity.to")?.let(java.time.Instant::parse) ?: end,
        savedState.get<String>("activity.granularity")?.let(ActivityGranularity::valueOf) ?: ActivityGranularity.DAY,
        savedState["activity.compare"] ?: false)
        private set
    private val mutableFilters = MutableStateFlow(ActivityFilters(
        savedState["activity.fromDay"] ?: query.from.atZone(zone).toLocalDate().toString(),
        savedState["activity.throughDay"] ?: query.to.atZone(zone).toLocalDate().toString(), query))
    val filters = mutableFilters.asStateFlow()
    val periodExpanded = savedState.getStateFlow("activity.periodExpanded", false)
    val evolutionExpanded = savedState.getStateFlow("activity.evolutionExpanded", false)
    fun expandPeriod(expanded: Boolean) { savedState["activity.periodExpanded"] = expanded }
    fun expandEvolution(expanded: Boolean) { savedState["activity.evolutionExpanded"] = expanded }
    private val mutableState = MutableStateFlow<ProviderActivityUiState>(ProviderActivityUiState.Loading)
    val uiState = mutableState.asStateFlow()
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
                    mutableState.value = ProviderActivityUiState.SessionExpired
                }
            }
        }
        retry()
    }

    val readingIndex: Int get() = savedState["activity.readingIndex"] ?: 0
    val readingOffset: Int get() = savedState["activity.readingOffset"] ?: 0
    fun rememberReadingPosition(index: Int, offset: Int) {
        savedState["activity.readingIndex"] = index
        savedState["activity.readingOffset"] = offset
    }

    fun editDates(fromDay: String, throughDay: String) {
        mutableFilters.value = mutableFilters.value.copy(fromDay = fromDay, throughDay = throughDay)
        savedState["activity.fromDay"] = fromDay
        savedState["activity.throughDay"] = throughDay
    }

    fun applyDates() {
        val filters = mutableFilters.value
        val now = clock.instant()
        val today = now.atZone(zone).toLocalDate()
        if (!filters.fromDay.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) ||
            !filters.throughDay.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
            mutableFilters.value = filters.copy(dateError = ActivityDateError.FORMAT)
            return
        }
        val fromDay: LocalDate
        val throughDay: LocalDate
        try {
            fromDay = LocalDate.parse(filters.fromDay)
            throughDay = LocalDate.parse(filters.throughDay)
        } catch (_: DateTimeParseException) {
            mutableFilters.value = filters.copy(dateError = ActivityDateError.FORMAT)
            return
        }
        val start = fromDay.atStartOfDay(zone).toInstant()
        val finish = if (throughDay == today) now else throughDay.plusDays(1).atStartOfDay(zone).toInstant()
        val error = when {
            fromDay > throughDay || start >= finish -> ActivityDateError.REVERSED
            throughDay > today -> ActivityDateError.FUTURE
            Duration.between(start, finish) > Duration.ofDays(365) -> ActivityDateError.TOO_LONG
            else -> null
        }
        if (error != null) {
            mutableFilters.value = filters.copy(dateError = error)
            return
        }
        changeQuery(query.copy(from = start, to = finish))
    }

    fun selectGranularity(granularity: ActivityGranularity) = changeQuery(query.copy(granularity = granularity))
    fun comparePrevious(enabled: Boolean) = changeQuery(query.copy(comparePrevious = enabled))

    private fun changeQuery(next: ActivityQuery) {
        mutableFilters.value = mutableFilters.value.copy(query = next, dateError = null)
        if (next == query) return
        query = next
        savedState["activity.from"] = next.from.toString()
        savedState["activity.to"] = next.to.toString()
        savedState["activity.granularity"] = next.granularity.name
        savedState["activity.compare"] = next.comparePrevious
        requestId++
        loadJob?.cancel()
        loadJob = null
        retry()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        val session = sessionStore.getSession()
        if (session == null) {
            mutableState.value = ProviderActivityUiState.SessionExpired
            return
        }
        val requestedQuery = query
        val id = ++requestId
        mutableState.value = ProviderActivityUiState.Loading
        loadJob = viewModelScope.launch {
            val outcome = getActivity(requestedQuery)
            if (id != requestId || sessionStore.getSession() != session) return@launch
            mutableState.value = when (outcome) {
                is ActivityOutcome.Success -> ProviderActivityUiState.Ready(outcome.activity)
                ActivityOutcome.Failure.Unauthorized -> {
                    sessionStore.clearSession()
                    ProviderActivityUiState.SessionExpired
                }
                is ActivityOutcome.Failure -> ProviderActivityUiState.Error(outcome)
            }
        }
    }
}
