package com.loresuelvo.serviceprovider.ui.screens.proposals

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.loresuelvo.serviceprovider.domain.proposal.*
import com.loresuelvo.serviceprovider.ui.proposals.ServiceProposalListUiState
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class CollectionProposalContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `requested proposal opens existing detail and its own conversation`() {
        val proposal = ServiceProposalSummary(8, 93, 1500050, 1791288000000, "Inspect sink", 45,
            ServiceProposalStatus.Accepted, 1790000000000,
            ServiceProposalCounterpart(7, "consumer", "Ana", "Pérez", null, null),
            ServiceProposalBookingTerms("ARS", 1500050, 1000, 1499050, 500, 100, 400, 1100, 1499450, 1500550, 1790000000000))
        var conversation: Int? = null
        compose.setContent { LoresuelvoTheme {
            CollectionProposalContent(8, ServiceProposalListUiState(proposals = listOf(proposal.copy(id = 9, description = "Other work"), proposal), loading = false), {}, { conversation = it }, {})
        } }
        compose.onNodeWithText("Inspect sink").assertExists()
        compose.onNodeWithText("Other work").assertDoesNotExist()
        compose.onNodeWithText("Ver conversación").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertEquals(93, conversation)
    }

    @Test fun `loading failure and missing proposal remain distinct and retryable`() {
        val state = mutableStateOf(ServiceProposalListUiState())
        var retries = 0
        compose.setContent { LoresuelvoTheme {
            CollectionProposalContent(8, state.value, {}, {}, { retries++ })
        } }
        compose.onNodeWithText("Esta propuesta ya no está disponible.").assertDoesNotExist()
        compose.runOnIdle { state.value = ServiceProposalListUiState(loading = false, failure = ServiceProposalListOutcome.Failure.Unavailable) }
        compose.onNodeWithText("Reintentar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertEquals(1, retries)
        compose.runOnIdle { state.value = ServiceProposalListUiState(loading = false) }
        compose.onNodeWithText("Esta propuesta ya no está disponible.").assertExists()
    }
}
