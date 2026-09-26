package com.loresuelvo.serviceprovider.ui.screens.proposals

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import com.loresuelvo.serviceprovider.R
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalSummary
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalStatus
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalCounterpart
import com.loresuelvo.serviceprovider.domain.proposal.ServiceProposalBookingTerms
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListUiState
import com.loresuelvo.serviceprovider.ui.proposals.ProposalTab
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceProposalListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `proposal card shows Argentine amount and local visit`() {
        val previousLocale = Locale.getDefault()
        val previousZone = TimeZone.getDefault()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previousConfiguration = android.content.res.Configuration(context.resources.configuration)
        try {
            Locale.setDefault(Locale.forLanguageTag("es-AR"))
            TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
            context.resources.updateConfiguration(
                android.content.res.Configuration(previousConfiguration).apply {
                    setLocale(Locale.forLanguageTag("es-AR"))
                }, context.resources.displayMetrics,
            )
            compose.setContent {
                LoresuelvoTheme {
                    ServiceProposalListScreen(
                        ServiceProposalListUiState(
                            proposals = listOf(proposal(12).copy(
                                scheduledOnEpochMillis = Instant.parse("2026-10-05T00:30:00Z").toEpochMilli(),
                                description = "Reparar la canilla de la cocina",
                                counterpart = proposal(12).counterpart.copy(categoryName = "Plomería"),
                            )),
                            loading = false,
                        ),
                        onSelectTab = {}, onRetry = {}, onBack = {},
                    )
                }
            }
            compose.onNodeWithText("Ana Pérez").assertIsDisplayed()
            compose.onNodeWithText("ARS 15.000,50").assertIsDisplayed()
            compose.onNodeWithText("el 4 de octubre de 2026 a las 21:30").assertIsDisplayed()
            compose.onNodeWithText("Reparar la canilla de la cocina").assertIsDisplayed()
            compose.onNodeWithText("Pendiente").assertIsDisplayed()
            compose.onNodeWithText("Plomería").assertDoesNotExist()
        } finally {
            Locale.setDefault(previousLocale)
            TimeZone.setDefault(previousZone)
            context.resources.updateConfiguration(previousConfiguration, context.resources.displayMetrics)
        }
    }

    @Test fun `missing and inaccessible photos leave consumer initials visible`() {
        val proposalState = mutableStateOf(proposal(12))
        compose.setContent {
            LoresuelvoTheme {
                ServiceProposalListScreen(
                    ServiceProposalListUiState(proposals = listOf(proposalState.value), loading = false),
                    onSelectTab = {}, onRetry = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText("AP").assertIsDisplayed()
        compose.runOnIdle {
            proposalState.value = proposalState.value.copy(counterpart = proposalState.value.counterpart.copy(
                profilePhotoUrl = "bad://missing-photo",
            ))
        }
        compose.onNodeWithText("AP").assertIsDisplayed()
    }

    @Test fun `pending tab and ordered proposal cards are visible`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            LoresuelvoTheme {
                ServiceProposalListScreen(
                    state = ServiceProposalListUiState(
                        proposals = listOf(12, 11, 10).map(::proposal),
                        loading = false,
                    ),
                    onSelectTab = {}, onRetry = {}, onBack = {},
                )
            }
        }
        compose.onNode(
            hasText(context.getString(R.string.proposal_list_pending)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.proposal_list_accepted)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.proposal_list_rejected)).assertIsDisplayed()
        listOf(12, 11, 10).forEach {
            compose.onNodeWithTag("proposal_list_items").performScrollToNode(
                hasText(context.getString(R.string.proposal_list_item, it)),
            )
            compose.onNodeWithText(context.getString(R.string.proposal_list_item, it)).assertIsDisplayed()
        }
    }

    @Test fun `each status is displayed in a badge`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val statusState = mutableStateOf(ServiceProposalStatus.Pending)
        compose.setContent {
            LoresuelvoTheme {
                val status = statusState.value
                ServiceProposalListScreen(
                    state = ServiceProposalListUiState(
                        selectedTab = when (status) {
                            ServiceProposalStatus.Pending -> com.loresuelvo.serviceprovider.ui.proposals.ProposalTab.Pending
                            ServiceProposalStatus.Accepted -> com.loresuelvo.serviceprovider.ui.proposals.ProposalTab.Accepted
                            ServiceProposalStatus.Rejected -> com.loresuelvo.serviceprovider.ui.proposals.ProposalTab.Rejected
                        },
                        proposals = listOf(proposal(12).copy(status = status)), loading = false,
                    ),
                    onSelectTab = {}, onRetry = {}, onBack = {},
                )
            }
        }
        listOf(
            ServiceProposalStatus.Pending to R.string.proposal_status_pending,
            ServiceProposalStatus.Accepted to R.string.proposal_status_accepted,
            ServiceProposalStatus.Rejected to R.string.proposal_status_rejected,
        ).forEach { (status, label) ->
            compose.runOnIdle { statusState.value = status }
            compose.onNodeWithTag("proposal_list_items").performScrollToNode(
                hasTestTag("proposal_status_badge"),
            )
            compose.onNode(hasTestTag("proposal_status_badge") and hasText(context.getString(label)))
                .assertExists()
        }
    }

    @Test fun `selecting a tab displays only its proposals`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selected = mutableStateOf(ProposalTab.Pending)
        compose.setContent {
            LoresuelvoTheme {
                ServiceProposalListScreen(
                    state = ServiceProposalListUiState(
                        selectedTab = selected.value,
                        proposals = listOf(
                            proposal(12),
                            proposal(22).copy(status = ServiceProposalStatus.Accepted),
                        ),
                        loading = false,
                    ),
                    onSelectTab = { selected.value = it }, onRetry = {}, onBack = {},
                )
            }
        }
        compose.onNodeWithText(context.getString(R.string.proposal_list_accepted)).performClick()
        compose.onNode(
            hasText(context.getString(R.string.proposal_list_accepted)) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.proposal_list_item, 22)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.proposal_list_item, 12)).assertDoesNotExist()
        compose.onNode(hasTestTag("proposal_status_badge") and
            hasText(context.getString(R.string.proposal_status_accepted))).assertExists()
    }

    private fun proposal(id: Int) = ServiceProposalSummary(
        id, 93, 1500050, Instant.parse("2026-10-06T12:00:00Z").toEpochMilli(), "Inspect sink", 45,
        ServiceProposalStatus.Pending, Instant.parse("2026-09-21T12:00:00Z").toEpochMilli(),
        ServiceProposalCounterpart(7, "consumer", "Ana", "Pérez", null, null),
        ServiceProposalBookingTerms(
            "ARS", 1500050, 1000, 1499050, 500, 100, 400, 1100, 1499450, 1500550,
            Instant.parse("2026-09-30T12:00:00Z").toEpochMilli(),
        ),
    )
}
