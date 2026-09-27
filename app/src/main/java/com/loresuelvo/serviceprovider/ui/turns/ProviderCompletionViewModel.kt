package com.loresuelvo.serviceprovider.ui.turns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.CompletionEligibility
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidencePreparer
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.usecase.activity.GetCompletionEligibilityUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionDraftValidation
import com.loresuelvo.serviceprovider.domain.usecase.activity.ValidateCompletionReportDraftUseCase
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionEvidenceUpload
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionUploadFailure
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionUploadStage
import com.loresuelvo.serviceprovider.domain.usecase.activity.UploadCompletionEvidenceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ProviderCompletionUiState {
    data object Closed : ProviderCompletionUiState
    data class Checking(val order: WorkOrder) : ProviderCompletionUiState
    data class Ready(val order: WorkOrder, val eligibility: CompletionEligibility) : ProviderCompletionUiState
    data object SessionExpired : ProviderCompletionUiState
}

data class CompletionEvidenceSelection(
    val id: Long,
    val source: String,
    val status: EvidenceSelectionStatus,
    val uploadStatus: EvidenceUploadStatus = EvidenceUploadStatus.NotStarted,
)

sealed interface EvidenceUploadStatus {
    data object NotStarted : EvidenceUploadStatus
    data object Uploading : EvidenceUploadStatus
    data class Confirmed(val fileId: String) : EvidenceUploadStatus
    data class Failed(val failure: CompletionEvidenceUpload.Failure) : EvidenceUploadStatus
}

sealed interface EvidenceSelectionStatus {
    data object Preparing : EvidenceSelectionStatus
    data class Ready(val image: PreparedEvidenceImage) : EvidenceSelectionStatus
    data class Invalid(val reason: EvidenceImagePreparation.Invalid) : EvidenceSelectionStatus
}

enum class EvidenceSelectionIssue { MaximumReached, AlreadySelected }

@HiltViewModel
class ProviderCompletionViewModel @Inject constructor(
    private val getEligibility: GetCompletionEligibilityUseCase,
    private val sessionStore: AuthSessionStore,
    private val evidencePreparer: CompletionEvidencePreparer,
    private val validateDraft: ValidateCompletionReportDraftUseCase,
    private val uploadEvidence: UploadCompletionEvidenceUseCase,
) : ViewModel() {
    private var activeSession = sessionStore.getSession()
    private var queryJob: Job? = null
    private var draftOrderId: Int? = null
    private var nextEvidenceId = 0L
    private val evidenceJobs = mutableMapOf<Long, Job>()
    private val uploadJobs = mutableMapOf<Long, Job>()
    private val _uiState = MutableStateFlow<ProviderCompletionUiState>(ProviderCompletionUiState.Closed)
    val uiState: StateFlow<ProviderCompletionUiState> = _uiState.asStateFlow()
    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()
    private val _evidence = MutableStateFlow<List<CompletionEvidenceSelection>>(emptyList())
    val evidence: StateFlow<List<CompletionEvidenceSelection>> = _evidence.asStateFlow()
    private val _evidenceIssue = MutableStateFlow<EvidenceSelectionIssue?>(null)
    val evidenceIssue: StateFlow<EvidenceSelectionIssue?> = _evidenceIssue.asStateFlow()
    private val _validationIssue = MutableStateFlow<CompletionDraftValidation.Invalid?>(null)
    val validationIssue: StateFlow<CompletionDraftValidation.Invalid?> = _validationIssue.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    queryJob?.cancel()
                    clearDraft()
                    _uiState.value = ProviderCompletionUiState.SessionExpired
                }
            }
        }
    }

    fun open(order: WorkOrder) {
        queryJob?.cancel()
        if (draftOrderId != order.id) {
            clearDraft()
            draftOrderId = order.id
        }
        val requestSession = sessionStore.getSession()
        if (requestSession == null) {
            clearDraft()
            _uiState.value = ProviderCompletionUiState.SessionExpired
            return
        }
        _uiState.value = ProviderCompletionUiState.Checking(order)
        queryJob = viewModelScope.launch {
            val eligibility = getEligibility(order)
            if (sessionStore.getSession() != requestSession) return@launch
            if (eligibility == CompletionEligibility.Failure.Unauthorized) {
                clearDraft()
                _uiState.value = ProviderCompletionUiState.SessionExpired
                sessionStore.clearSession()
            } else {
                _uiState.value = ProviderCompletionUiState.Ready(order, eligibility)
            }
        }
    }

    fun retry() {
        val order = (_uiState.value as? ProviderCompletionUiState.Ready)?.order ?: return
        open(order)
    }

    fun onDescriptionChange(value: String) {
        if ((_uiState.value as? ProviderCompletionUiState.Ready)?.eligibility == CompletionEligibility.Eligible) {
            _description.value = value
            _validationIssue.value = null
        }
    }

    fun selectEvidence(sources: List<String>) {
        if ((_uiState.value as? ProviderCompletionUiState.Ready)?.eligibility != CompletionEligibility.Eligible)
            return
        _evidenceIssue.value = null
        _validationIssue.value = null
        sources.forEach { source ->
            if (_evidence.value.size >= 3) {
                _evidenceIssue.value = EvidenceSelectionIssue.MaximumReached
                return@forEach
            }
            if (_evidence.value.any { it.source == source }) {
                _evidenceIssue.value = EvidenceSelectionIssue.AlreadySelected
                return@forEach
            }
            val id = ++nextEvidenceId
            _evidence.value += CompletionEvidenceSelection(id, source, EvidenceSelectionStatus.Preparing)
            evidenceJobs[id] = viewModelScope.launch {
                val result = try {
                    evidencePreparer.prepare(source)
                } catch (e: CancellationException) {
                    return@launch
                }
                evidenceJobs.remove(id)
                if (_evidence.value.none { it.id == id }) {
                    if (result is EvidenceImagePreparation.Ready) {
                        viewModelScope.launch { evidencePreparer.clean(result.image) }
                    }
                    return@launch
                }
                val status = when (result) {
                    is EvidenceImagePreparation.Ready -> EvidenceSelectionStatus.Ready(result.image)
                    is EvidenceImagePreparation.Invalid -> EvidenceSelectionStatus.Invalid(result)
                }
                _evidence.value = _evidence.value.map { if (it.id == id) it.copy(status = status) else it }
            }
        }
    }

    fun removeEvidence(id: Long) {
        val selected = _evidence.value.firstOrNull { it.id == id } ?: return
        evidenceJobs.remove(id)?.cancel()
        uploadJobs.remove(id)?.cancel()
        _evidence.value = _evidence.value.filterNot { it.id == id }
        _evidenceIssue.value = null
        _validationIssue.value = null
        if (selected.status is EvidenceSelectionStatus.Ready) {
            viewModelScope.launch { evidencePreparer.clean(selected.status.image) }
        }
    }

    fun uploadEvidence(id: Long) {
        val selected = _evidence.value.firstOrNull { it.id == id } ?: return
        if (selected.uploadStatus != EvidenceUploadStatus.NotStarted) return
        startUpload(selected)
    }

    fun retryEvidence(id: Long) {
        val selected = _evidence.value.firstOrNull { it.id == id } ?: return
        if (selected.uploadStatus !is EvidenceUploadStatus.Failed) return
        startUpload(selected)
    }

    private fun startUpload(selected: CompletionEvidenceSelection) {
        if ((_uiState.value as? ProviderCompletionUiState.Ready)?.eligibility != CompletionEligibility.Eligible) return
        val image = (selected.status as? EvidenceSelectionStatus.Ready)?.image ?: return
        val session = sessionStore.getSession() ?: return
        val orderId = draftOrderId
        _evidence.value = _evidence.value.map { if (it.id == selected.id) it.copy(uploadStatus = EvidenceUploadStatus.Uploading) else it }
        uploadJobs[selected.id] = viewModelScope.launch {
            val outcome = try {
                uploadEvidence(image)
            } catch (e: CancellationException) {
                return@launch
            }
            uploadJobs.remove(selected.id)
            if (sessionStore.getSession() != session || draftOrderId != orderId ||
                _evidence.value.none { it.id == selected.id && it.uploadStatus == EvidenceUploadStatus.Uploading }) return@launch
            if (outcome is CompletionEvidenceUpload.Failure &&
                outcome.reason == CompletionUploadFailure.UNAUTHORIZED &&
                outcome.stage != CompletionUploadStage.TRANSFER) {
                clearDraft()
                _uiState.value = ProviderCompletionUiState.SessionExpired
                sessionStore.clearSession()
                return@launch
            }
            val status = when (outcome) {
                is CompletionEvidenceUpload.Success -> EvidenceUploadStatus.Confirmed(outcome.confirmedFileId)
                is CompletionEvidenceUpload.Failure -> EvidenceUploadStatus.Failed(outcome)
            }
            _evidence.value = _evidence.value.map { if (it.id == selected.id) it.copy(uploadStatus = status) else it }
            _validationIssue.value = null
        }
    }

    fun discardDraft() {
        queryJob?.cancel()
        clearDraft()
        _uiState.value = ProviderCompletionUiState.Closed
    }

    fun attemptSubmit(): CompletionDraftValidation? {
        if ((_uiState.value as? ProviderCompletionUiState.Ready)?.eligibility != CompletionEligibility.Eligible)
            return null
        val result = validateDraft(_description.value, _evidence.value.map {
            (it.uploadStatus as? EvidenceUploadStatus.Confirmed)?.fileId
        })
        _validationIssue.value = result as? CompletionDraftValidation.Invalid
        return result
    }

    private fun clearDraft() {
        _description.value = ""
        draftOrderId = null
        evidenceJobs.values.forEach { it.cancel() }
        evidenceJobs.clear()
        uploadJobs.values.forEach { it.cancel() }
        uploadJobs.clear()
        val prepared = _evidence.value.mapNotNull { (it.status as? EvidenceSelectionStatus.Ready)?.image }
        _evidence.value = emptyList()
        _evidenceIssue.value = null
        _validationIssue.value = null
        prepared.forEach { image -> viewModelScope.launch { evidencePreparer.clean(image) } }
    }
}
