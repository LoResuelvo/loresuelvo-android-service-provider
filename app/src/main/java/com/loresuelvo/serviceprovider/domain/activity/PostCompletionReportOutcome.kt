package com.loresuelvo.serviceprovider.domain.activity

sealed interface PostCompletionReportOutcome {
    data class Success(val reportId: Int) : PostCompletionReportOutcome

    sealed interface Rejected : PostCompletionReportOutcome {
        data object InvalidData : Rejected
        data object Unauthorized : Rejected
        data object Forbidden : Rejected
        data object NotFound : Rejected
        data object Conflict : Rejected
        data class Other(val statusCode: Int) : Rejected
    }

    /** The server may have saved the report; callers must reconcile with GET before another POST. */
    sealed interface Uncertain : PostCompletionReportOutcome {
        data object Network : Uncertain
        data class Server(val statusCode: Int) : Uncertain
        data object InvalidResponse : Uncertain
    }
}
