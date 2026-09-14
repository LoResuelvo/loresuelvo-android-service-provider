package com.loresuelvo.serviceprovider.bdd.jobrequest

import com.loresuelvo.serviceprovider.domain.activity.AcceptJobRequestOutcome
import com.loresuelvo.serviceprovider.domain.activity.ActivityLoadOutcome
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.activity.JobRequestRepository
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderRepository
import java.util.ArrayDeque

internal class JobRequestRepositoryFake : JobRequestRepository {
    var pending: ActivityLoadOutcome.Success<JobRequest> = ActivityLoadOutcome.Success(emptyList())
    val acceptOutcomes = ArrayDeque<AcceptJobRequestOutcome>()
    var acceptCalls = 0
        private set

    fun reset() {
        pending = ActivityLoadOutcome.Success(emptyList())
        acceptOutcomes.clear()
        acceptCalls = 0
    }

    override suspend fun getPendingJobRequests(): ActivityLoadOutcome<JobRequest> = pending

    override suspend fun acceptJobRequest(id: Int): AcceptJobRequestOutcome {
        acceptCalls += 1
        return if (acceptOutcomes.isEmpty()) {
            AcceptJobRequestOutcome.Success(id, 11)
        } else {
            acceptOutcomes.removeFirst()
        }
    }
}

internal class EmptyWorkOrderRepository : WorkOrderRepository {
    override suspend fun getWorkOrders(): ActivityLoadOutcome<WorkOrder> =
        ActivityLoadOutcome.Success(emptyList())
}
