package com.loresuelvo.serviceprovider.domain.conversation

sealed interface ConversationsOutcome {
    data class Success(val conversations: List<Conversation>) : ConversationsOutcome

    sealed interface Failure : ConversationsOutcome {
        data class Network(val cause: Throwable) : Failure
        data class Server(val code: Int) : Failure
        data object Unauthorized : Failure
        data class Invalid(val cause: Throwable? = null) : Failure
    }
}
