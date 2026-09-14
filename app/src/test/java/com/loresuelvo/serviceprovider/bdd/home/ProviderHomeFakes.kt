package com.loresuelvo.serviceprovider.bdd.home

import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountOutcome
import com.loresuelvo.serviceprovider.domain.account.CurrentAccountRepository
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import com.loresuelvo.serviceprovider.domain.auth.AuthSession
import com.loresuelvo.serviceprovider.domain.auth.AuthSessionStore
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal class ProviderHomeSessionStore(initial: AuthSession? = null) : AuthSessionStore {
    private val state = MutableStateFlow(initial)
    var clearCalls: Int = 0
        private set

    override val sessionFlow: StateFlow<AuthSession?> = state
    override fun getSession(): AuthSession? = state.value
    override fun saveSession(session: AuthSession) {
        state.value = session
    }
    override fun clearSession() {
        clearCalls += 1
        state.value = null
    }
}

internal class ProviderHomeCurrentAccountFake : CurrentAccountRepository {
    val responses = ArrayDeque<CurrentAccountOutcome>()
    var defaultResponse: CurrentAccountOutcome = CurrentAccountOutcome.Failure.NotFound
    var gate: CompletableDeferred<Unit>? = null
    var calls: Int = 0

    override suspend fun getCurrentAccount(): CurrentAccountOutcome {
        calls += 1
        gate?.await()
        return if (responses.isEmpty()) defaultResponse else responses.removeFirst()
    }
}

internal class ProviderHomeJobRequestFake : JobRequestRepository {
    val responses = ArrayDeque<ActivityLoadOutcome<JobRequest>>()
    var defaultResponse: ActivityLoadOutcome<JobRequest> = ActivityLoadOutcome.Success(emptyList())
    var calls: Int = 0

    override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> {
        calls += 1
        return if (responses.isEmpty()) defaultResponse else responses.removeFirst()
    }

    override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome =
        AcceptJobRequestOutcome.Failure.Invalid
}

internal class ProviderHomeWorkOrderFake : WorkOrderRepository {
    val responses = ArrayDeque<ActivityLoadOutcome<WorkOrder>>()
    var defaultResponse: ActivityLoadOutcome<WorkOrder> = ActivityLoadOutcome.Success(emptyList())
    var calls: Int = 0

    override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> {
        calls += 1
        return if (responses.isEmpty()) defaultResponse else responses.removeFirst()
    }
}
