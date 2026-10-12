package com.loresuelvo.serviceprovider.acceptance.jobrequest

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.account.CurrentAccount
import com.loresuelvo.serviceprovider.domain.activity.JobRequest
import com.loresuelvo.serviceprovider.domain.category.Category
import com.loresuelvo.serviceprovider.ui.home.ActivitySectionState
import com.loresuelvo.serviceprovider.ui.home.ProviderHomeUiState
import com.loresuelvo.serviceprovider.ui.navigation.LoResuelvoNavHost
import com.loresuelvo.serviceprovider.ui.navigation.Route
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListUiState
import com.loresuelvo.serviceprovider.ui.screens.home.ProviderHomeScreen
import com.loresuelvo.serviceprovider.ui.screens.jobrequest.JobRequestListScreen
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JobRequestListNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun view_all_opens_pending_requests_and_back_restores_home() {
        val request = JobRequest(41, "Ana Pérez", "Plomería", "Reparar la canilla")
        var selected: JobRequest? = null
        var requests by mutableStateOf(listOf(request))
        compose.setContent { LoresuelvoTheme {
            val nav = rememberNavController()
            LoResuelvoNavHost(nav, Route.Home.path,
                welcome = {}, professionalProfile = {}, optionalIdentityVerification = {},
                home = {
                    ProviderHomeScreen(CurrentAccount.Provider(1, "Carlos", "Gómez", "provider@example.com",
                        Category(1, "Plomería"), null),
                        ProviderHomeUiState(ActivitySectionState.Ready(requests), ActivitySectionState.Ready(emptyList())),
                        {}, {}, {}, {}, proposalsState = ServiceProposalListUiState(loading = false),
                        onAllRequestsClick = { nav.navigate(Route.JobRequests.path) })
                },
                jobRequests = {
                    JobRequestListScreen(ActivitySectionState.Ready(requests), false,
                        { nav.popBackStack() }, {}, { selected = it })
                },
                messages = {}, profile = {}, jobRequestDetail = {}, conversation = {},
            )
        } }
        compose.onNodeWithTag("requests_view_all").performScrollTo().performClick()
        compose.onNodeWithTag("requests_list").assertIsDisplayed()
        compose.onNodeWithTag("request_details_41").performClick()
        assertEquals(request, selected)
        compose.runOnIdle { requests = emptyList() }
        compose.onNodeWithText(compose.activity.getString(R.string.provider_home_requests_empty)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.ui_back)).performClick()
        compose.onNodeWithTag("requests_view_all").assertIsDisplayed()
        compose.onNodeWithTag("home_request_41").assertDoesNotExist()
    }
}
