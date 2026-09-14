package com.loresuelvo.serviceprovider.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteTest {

    @Test
    fun builds_conversation_path_from_a_positive_id() {
        assertEquals("conversation/42", Route.Conversation.buildPath(42))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_a_non_positive_conversation_id() {
        Route.Conversation.buildPath(0)
    }
}
