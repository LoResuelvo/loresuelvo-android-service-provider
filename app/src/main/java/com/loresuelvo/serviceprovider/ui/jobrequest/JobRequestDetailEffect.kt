package com.loresuelvo.serviceprovider.ui.jobrequest

sealed interface JobRequestDetailEffect {
    data class Accepted(
        val requestId: Int,
        val conversationId: Int,
    ) : JobRequestDetailEffect
}
