package com.loresuelvo.serviceprovider.ui.screens.proposals

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
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
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceProposalListScreenTest {
    @get:Rule val compose = createComposeRule()

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
