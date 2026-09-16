package com.loresuelvo.serviceprovider.bdd.messaging

import com.loresuelvo.serviceprovider.ui.components.bottomnav.BottomDestination
import com.loresuelvo.serviceprovider.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal class ProviderMessageInboxWorld {

    private var currentRoute: String = Route.Home.path
    private var selectedRoute: String? = null
    private var conversationPath: String? = null
    private var conversationNavigationCount: Int = 0

    fun openHome() {
        currentRoute = Route.Home.path
    }

    fun selectMessages() {
        assertTrue(BottomDestination.shouldShow(currentRoute))
        selectedRoute = BottomDestination.Messages.route
        currentRoute = selectedRoute.orEmpty()
    }

    fun assertMessagesSelected() {
        assertEquals(Route.Messages.path, currentRoute)
        assertEquals(Route.Messages.path, selectedRoute)
    }

    fun assertBottomBarVisibility() {
        assertTrue(BottomDestination.shouldShow(Route.Home.path))
        assertTrue(BottomDestination.shouldShow(Route.Messages.path))
        assertFalse(BottomDestination.shouldShow(Route.Welcome.path))
        assertFalse(BottomDestination.shouldShow(Route.Conversation.path))
    }

    fun openMessagesWithConversation() {
        currentRoute = Route.Messages.path
        selectedRoute = Route.Messages.path
        conversationPath = null
        conversationNavigationCount = 0
    }

    fun selectConversation() {
        conversationPath = Route.Conversation.buildPath(42)
        conversationNavigationCount += 1
    }

    fun assertConversationRoute() {
        assertEquals(Route.Conversation.buildPath(42), conversationPath)
        assertEquals(1, conversationNavigationCount)
    }

    fun assertBackToInbox() {
        currentRoute = Route.Messages.path
        assertEquals(Route.Messages.path, currentRoute)
        assertEquals(Route.Messages.path, selectedRoute)
    }

    fun reset() {
        currentRoute = Route.Home.path
        selectedRoute = null
        conversationPath = null
        conversationNavigationCount = 0
    }
}
