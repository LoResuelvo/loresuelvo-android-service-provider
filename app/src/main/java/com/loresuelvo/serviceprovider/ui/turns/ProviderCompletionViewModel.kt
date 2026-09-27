package com.loresuelvo.serviceprovider.ui.turns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.loresuelvo.serviceprovider.domain.activity.CompletionEligibility
import com.loresuelvo.serviceprovider.domain.activity.CompletionEvidencePreparer
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderDetailOutcome
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.activity.PostCompletionReportOutcome
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
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
    data object Interrupted : EvidenceUploadStatus
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

sealed interface CompletionSubmissionState {
    data object Idle : CompletionSubmissionState
    data object Checking : CompletionSubmissionState
    data object Sending : CompletionSubmissionState
    data object Reconciling : CompletionSubmissionState
    data object QueryFailed : CompletionSubmissionState
    data class Confirmed(val reportId: Int?, val serverConfirmed: Boolean) : CompletionSubmissionState
    data class Rejected(val outcome: PostCompletionReportOutcome.Rejected) : CompletionSubmissionState
    data class Blocked(val eligibility: CompletionEligibility) : CompletionSubmissionState
}

@HiltViewModel
class ProviderCompletionViewModel @Inject constructor(
    private val getEligibility: GetCompletionEligibilityUseCase,
    private val sessionStore: AuthSessionStore,
    private val evidencePreparer: CompletionEvidencePreparer,
    private val validateDraft: ValidateCompletionReportDraftUseCase,
    private val uploadEvidence: UploadCompletionEvidenceUseCase,
    private val workOrders: WorkOrderRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private var activeSession = sessionStore.getSession()
    private var queryJob: Job? = null
    private var submitJob: Job? = null
    private var draftOrderId: Int? = null
    private var nextEvidenceId = 0L
    private var restoredDraft = false
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
    private val _submission = MutableStateFlow<CompletionSubmissionState>(CompletionSubmissionState.Idle)
    val submission: StateFlow<CompletionSubmissionState> = _submission.asStateFlow()
    private val _refreshedOrderStatus = MutableStateFlow<WorkOrderStatus?>(null)
    val refreshedOrderStatus: StateFlow<WorkOrderStatus?> = _refreshedOrderStatus.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session != activeSession) {
                    activeSession = session
                    queryJob?.cancel()
                    submitJob?.cancel()
                    clearDraft()
                    _submission.value = CompletionSubmissionState.Idle
                    _uiState.value = ProviderCompletionUiState.SessionExpired
                }
            }
        }
    }

    fun open(order: WorkOrder) {
        if (_submission.value == CompletionSubmissionState.Checking ||
            _submission.value == CompletionSubmissionState.Sending ||
            _submission.value == CompletionSubmissionState.Reconciling) return
        queryJob?.cancel()
        if (draftOrderId != order.id) {
            clearDraft(savedStateHandle.get<Int>("completion_draft_order_id") != order.id ||
                savedStateHandle.get<String>("completion_draft_owner_id") != sessionStore.getSession()?.user?.id)
            draftOrderId = order.id
            restoredDraft = false
        }
        val requestSession = sessionStore.getSession()
        if (requestSession == null) {
            clearDraft()
            _uiState.value = ProviderCompletionUiState.SessionExpired
            return
        }
        val pendingOrder = savedStateHandle.get<Int>("completion_pending_order_id")
        val pendingOwner = savedStateHandle.get<String>("completion_pending_owner_id")
        if (pendingOrder != null && pendingOwner != requestSession.user.id) {
            clearSubmissionMarker()
            _submission.value = CompletionSubmissionState.Idle
        }
        if (pendingOrder != null && pendingOwner == requestSession.user.id && pendingOrder != order.id) {
            _submission.value = CompletionSubmissionState.Blocked(CompletionEligibility.ChangedOrder)
            _uiState.value = ProviderCompletionUiState.Ready(order, CompletionEligibility.ChangedOrder)
            return
        }
        _uiState.value = ProviderCompletionUiState.Checking(order)
        queryJob = viewModelScope.launch {
            if (!restoredDraft) {
                restoreDraft(order, requestSession)
                restoredDraft = true
            }
            if (savedStateHandle.get<Int>("completion_pending_order_id") == order.id) {
                _submission.value = CompletionSubmissionState.Reconciling
                reconcile(order, requestSession)
                return@launch
            }
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
        if (canEdit()) {
            _description.value = value
            saveDraft()
            _validationIssue.value = null
        }
    }

    fun selectEvidence(sources: List<String>) {
        if (!canEdit())
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
            saveDraft()
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
                saveDraft()
            }
        }
    }

    fun removeEvidence(id: Long) {
        if (!canEdit()) return
        val selected = _evidence.value.firstOrNull { it.id == id } ?: return
        evidenceJobs.remove(id)?.cancel()
        uploadJobs.remove(id)?.cancel()
        _evidence.value = _evidence.value.filterNot { it.id == id }
        saveDraft()
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
        if (selected.uploadStatus !is EvidenceUploadStatus.Failed &&
            selected.uploadStatus != EvidenceUploadStatus.Interrupted) return
        startUpload(selected)
    }

    private fun startUpload(selected: CompletionEvidenceSelection) {
        if (!canEdit()) return
        val image = (selected.status as? EvidenceSelectionStatus.Ready)?.image ?: return
        val session = sessionStore.getSession() ?: return
        val orderId = draftOrderId
        _evidence.value = _evidence.value.map { if (it.id == selected.id) it.copy(uploadStatus = EvidenceUploadStatus.Uploading) else it }
        saveDraft()
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
            saveDraft()
            _validationIssue.value = null
        }
    }

    fun discardDraft() {
        queryJob?.cancel()
        submitJob?.cancel()
        clearDraft()
        _submission.value = CompletionSubmissionState.Idle
        _uiState.value = ProviderCompletionUiState.Closed
    }

    fun attemptSubmit(): CompletionDraftValidation? {
        if (!canEdit())
            return null
        val result = validateDraft(_description.value, _evidence.value.map {
            (it.uploadStatus as? EvidenceUploadStatus.Confirmed)?.fileId
        })
        _validationIssue.value = result as? CompletionDraftValidation.Invalid
        return result
    }

    fun confirmCompletion() {
        val ready = _uiState.value as? ProviderCompletionUiState.Ready ?: return
        val valid = attemptSubmit() as? CompletionDraftValidation.Valid ?: return
        val session = sessionStore.getSession() ?: return
        val order = ready.order
        _submission.value = CompletionSubmissionState.Checking
        submitJob = viewModelScope.launch {
            val eligibility = getEligibility(order)
            if (!isCurrent(order, session)) return@launch
            if (eligibility == CompletionEligibility.Failure.Unauthorized) {
                expireSession()
                return@launch
            }
            if (eligibility != CompletionEligibility.Eligible) {
                _uiState.value = ProviderCompletionUiState.Ready(order, eligibility)
                _submission.value = CompletionSubmissionState.Idle
                return@launch
            }
            savedStateHandle["completion_pending_order_id"] = order.id
            savedStateHandle["completion_pending_owner_id"] = session.user.id
            _submission.value = CompletionSubmissionState.Sending
            val outcome = workOrders.postCompletionReport(order.id, valid.description, valid.confirmedFileIds)
            if (!isCurrent(order, session)) return@launch
            when (outcome) {
                is PostCompletionReportOutcome.Success -> {
                    savedStateHandle["completion_known_report_id"] = outcome.reportId
                    _submission.value = CompletionSubmissionState.Confirmed(outcome.reportId, false)
                    _uiState.value = ProviderCompletionUiState.Ready(order, CompletionEligibility.AlreadyReported)
                    reconcile(order, session)
                }
                is PostCompletionReportOutcome.Uncertain -> {
                    _submission.value = CompletionSubmissionState.Reconciling
                    reconcile(order, session)
                }
                is PostCompletionReportOutcome.Rejected -> {
                    clearSubmissionMarker()
                    when (outcome) {
                        PostCompletionReportOutcome.Rejected.Unauthorized -> expireSession()
                        PostCompletionReportOutcome.Rejected.Conflict -> {
                            _submission.value = CompletionSubmissionState.Rejected(outcome)
                            refreshAfterConflict(order, session)
                        }
                        else -> _submission.value = CompletionSubmissionState.Rejected(outcome)
                    }
                }
            }
        }
    }

    fun retryConflictQuery() {
        if (_submission.value != CompletionSubmissionState.Rejected(PostCompletionReportOutcome.Rejected.Conflict)) return
        val order = (_uiState.value as? ProviderCompletionUiState.Ready)?.order ?: return
        val session = sessionStore.getSession() ?: return
        queryJob?.cancel()
        queryJob = viewModelScope.launch { refreshAfterConflict(order, session) }
    }

    private suspend fun refreshAfterConflict(order: WorkOrder, session: AuthSession) {
        val eligibility = getEligibility(order)
        if (!isCurrent(order, session)) return
        if (eligibility == CompletionEligibility.Failure.Unauthorized) {
            expireSession()
            return
        }
        _uiState.value = ProviderCompletionUiState.Ready(order,
            if (eligibility == CompletionEligibility.Eligible) CompletionEligibility.ChangedOrder else eligibility)
    }

    fun retryReconciliation() {
        if (_submission.value != CompletionSubmissionState.QueryFailed &&
            _submission.value !is CompletionSubmissionState.Confirmed) return
        val order = (_uiState.value as? ProviderCompletionUiState.Ready)?.order ?: return
        if (savedStateHandle.get<Int>("completion_pending_order_id") != order.id) return
        val session = sessionStore.getSession() ?: return
        _submission.value = CompletionSubmissionState.Reconciling
        queryJob = viewModelScope.launch { reconcile(order, session) }
    }

    private suspend fun reconcile(order: WorkOrder, session: AuthSession) {
        val eligibility = getEligibility(order)
        if (!isCurrent(order, session)) return
        if (eligibility == CompletionEligibility.Failure.Unauthorized) {
            expireSession()
            return
        }
        val knownReportId = savedStateHandle.get<Int>("completion_known_report_id")
        when {
            eligibility == CompletionEligibility.AlreadyReported -> {
                val refreshed = (workOrders.getWorkOrder(order.id) as? WorkOrderDetailOutcome.Success)?.order
                if (!isCurrent(order, session)) return
                val verified = refreshed?.takeIf {
                    it.id == order.id && it.serviceProposalId == order.serviceProposalId &&
                        it.consumerId == order.consumerId && it.completionReportId != null &&
                        (knownReportId == null || it.completionReportId == knownReportId)
                }
                _refreshedOrderStatus.value = verified?.status
                if (verified != null) clearSubmissionMarker()
                _submission.value = when {
                    verified != null -> CompletionSubmissionState.Confirmed(knownReportId, true)
                    knownReportId != null -> CompletionSubmissionState.Confirmed(knownReportId, false)
                    else -> CompletionSubmissionState.QueryFailed
                }
                _uiState.value = ProviderCompletionUiState.Ready(order, eligibility)
            }
            knownReportId != null -> {
                _submission.value = CompletionSubmissionState.Confirmed(knownReportId, false)
                _uiState.value = ProviderCompletionUiState.Ready(order, CompletionEligibility.AlreadyReported)
            }
            eligibility == CompletionEligibility.Eligible -> {
                clearSubmissionMarker()
                _submission.value = CompletionSubmissionState.Idle
                _uiState.value = ProviderCompletionUiState.Ready(order, eligibility)
            }
            eligibility is CompletionEligibility.Failure -> {
                _submission.value = CompletionSubmissionState.QueryFailed
                _uiState.value = ProviderCompletionUiState.Ready(order, eligibility)
            }
            else -> {
                _submission.value = CompletionSubmissionState.Blocked(eligibility)
                _uiState.value = ProviderCompletionUiState.Ready(order, eligibility)
            }
        }
    }

    private fun canEdit(): Boolean = (_submission.value == CompletionSubmissionState.Idle ||
        _submission.value == CompletionSubmissionState.Rejected(PostCompletionReportOutcome.Rejected.InvalidData)) &&
        (_uiState.value as? ProviderCompletionUiState.Ready)?.eligibility == CompletionEligibility.Eligible

    private fun isCurrent(order: WorkOrder, session: AuthSession): Boolean =
        draftOrderId == order.id && sessionStore.getSession() == session

    private fun expireSession() {
        clearDraft()
        _submission.value = CompletionSubmissionState.Idle
        _uiState.value = ProviderCompletionUiState.SessionExpired
        sessionStore.clearSession()
    }

    private fun clearSubmissionMarker() {
        savedStateHandle.remove<Int>("completion_pending_order_id")
        savedStateHandle.remove<String>("completion_pending_owner_id")
        savedStateHandle.remove<Int>("completion_known_report_id")
    }

    private fun saveDraft() {
        val orderId = draftOrderId ?: return
        val owner = sessionStore.getSession()?.user?.id ?: return
        savedStateHandle["completion_draft_order_id"] = orderId
        savedStateHandle["completion_draft_owner_id"] = owner
        savedStateHandle["completion_draft_description"] = _description.value
        savedStateHandle["completion_draft_photos"] = ArrayList(_evidence.value.flatMap { selection ->
            val image = (selection.status as? EvidenceSelectionStatus.Ready)?.image
            listOf(selection.source, image?.originalName.orEmpty(), image?.mimeType.orEmpty(),
                image?.sizeBytes?.toString().orEmpty(), image?.localPath.orEmpty(),
                (selection.uploadStatus as? EvidenceUploadStatus.Confirmed)?.fileId.orEmpty())
        })
    }

    private suspend fun restoreDraft(order: WorkOrder, session: AuthSession) {
        if (savedStateHandle.get<Int>("completion_draft_order_id") != order.id ||
            savedStateHandle.get<String>("completion_draft_owner_id") != session.user.id) return
        val fields = savedStateHandle.get<ArrayList<String>>("completion_draft_photos") ?: return
        if (fields.size > 18 || fields.size % 6 != 0) return
        val restored = fields.chunked(6).map { entry ->
            val image = entry[3].toLongOrNull()?.let {
                PreparedEvidenceImage(entry[1], entry[2], it, entry[4])
            }
            val available = image != null && evidencePreparer.isAvailable(image)
            val status = if (available) EvidenceSelectionStatus.Ready(image!!) else
                EvidenceSelectionStatus.Invalid(EvidenceImagePreparation.Invalid.Unreadable)
            CompletionEvidenceSelection(++nextEvidenceId, entry[0], status,
                if (available && entry[5].isNotBlank()) EvidenceUploadStatus.Confirmed(entry[5])
                else EvidenceUploadStatus.Interrupted)
        }
        if (!isCurrent(order, session)) return
        _description.value = savedStateHandle.get<String>("completion_draft_description").orEmpty()
        _evidence.value = restored
        saveDraft()
    }

    private fun clearDraft(clearSaved: Boolean = true) {
        if (clearSaved) {
            savedStateHandle.remove<Int>("completion_draft_order_id")
            savedStateHandle.remove<String>("completion_draft_owner_id")
            savedStateHandle.remove<String>("completion_draft_description")
            savedStateHandle.remove<ArrayList<String>>("completion_draft_photos")
        }
        _description.value = ""
        _refreshedOrderStatus.value = null
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
