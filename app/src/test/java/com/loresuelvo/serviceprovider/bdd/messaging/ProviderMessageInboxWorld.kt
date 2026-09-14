package com.loresuelvo.serviceprovider.bdd.messaging

import com.loresuelvo.serviceprovider.ui.navigation.ProviderBottomDestination
import com.loresuelvo.serviceprovider.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

internal class ProviderMessageInboxWorld {

    private var currentRoute: String = Route.Home.path
    private var selectedRoute: String? = null

    fun openHome() {
        currentRoute = Route.Home.path
    }

    fun selectMessages() {
        assertTrue(ProviderBottomDestination.shouldShow(currentRoute))
        selectedRoute = ProviderBottomDestination.Messages.route
        currentRoute = selectedRoute.orEmpty()
    }

    fun assertMessagesSelected() {
        assertEquals(Route.Messages.path, currentRoute)
        assertEquals(Route.Messages.path, selectedRoute)
    }

    fun assertBottomBarVisibility() {
        assertTrue(ProviderBottomDestination.shouldShow(Route.Home.path))
        assertTrue(ProviderBottomDestination.shouldShow(Route.Messages.path))
        assertFalse(ProviderBottomDestination.shouldShow(Route.Welcome.path))
        assertFalse(ProviderBottomDestination.shouldShow(Route.Conversation.path))
    }

    fun reset() {
        currentRoute = Route.Home.path
        selectedRoute = null
    }
}
