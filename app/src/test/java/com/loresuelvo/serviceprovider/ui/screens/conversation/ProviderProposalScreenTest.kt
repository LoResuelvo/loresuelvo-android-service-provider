package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.runtime.mutableStateOf
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es-rAR", sdk = [34])
class ProviderProposalScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun formShowsConsumerAndEditableFields() {
        compose.setContent {
            LoresuelvoTheme {
                ProviderProposalScreen(
                    form = ProposalUiState.Form(42, 7, "Ana Pérez"),
                    onAmountChange = {}, onDateChange = {}, onTimeChange = {},
                    onReasonChange = {}, onDurationSelect = {}, onCustomDurationChange = {}, onClose = {},
                )
            }
        }
        compose.onNodeWithTag(PROPOSAL_FORM_TAG).assertIsDisplayed()
        compose.onNodeWithText("Consumidor: Ana Pérez").assertIsDisplayed()
        listOf("Monto", "Fecha: ", "Hora: ", "Motivo de la visita", "Duración estimada")
            .forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithTag(PROPOSAL_DURATION_TAG).assertExists()
    }

    @Test
    fun durationMenuSelectsPresetAndCustomFieldEdits() {
        val selected = mutableStateOf<Int?>(-1)
        val custom = mutableStateOf("")
        compose.setContent {
            LoresuelvoTheme {
                ProviderProposalScreen(
                    form = ProposalUiState.Form(42, 7, "Ana Pérez", duration = custom.value,
                        customDuration = selected.value == null),
                    onAmountChange = {}, onDateChange = {}, onTimeChange = {},
                    onReasonChange = {}, onDurationSelect = { selected.value = it },
                    onCustomDurationChange = { custom.value = it }, onClose = {},
                )
            }
        }
        compose.onNodeWithTag(PROPOSAL_DURATION_TAG).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("15 minutos").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(15, selected.value)
        compose.onNodeWithTag(PROPOSAL_DURATION_TAG).performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Personalizada").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(null, selected.value)
        compose.onNodeWithTag(PROPOSAL_CUSTOM_DURATION_TAG).performTextInput("75")
        assertEquals("75", custom.value)
    }

    @Test
    @Config(qualifiers = "w800dp-h600dp")
    fun wideFormIsConstrained() {
        compose.setContent {
            LoresuelvoTheme {
                ProviderProposalScreen(ProposalUiState.Form(42, 7, "Ana Pérez"),
                    {}, {}, {}, {}, {}, {}, {})
            }
        }
        val form = compose.onNodeWithTag(PROPOSAL_FORM_TAG).getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val formWidth = form.right - form.left
        val rootWidth = root.right - root.left
        assertTrue(formWidth < rootWidth)
    }

    @Test
    @Config(qualifiers = "w400dp-h400dp")
    fun compactFormScrollsToDuration() {
        compose.setContent {
            LoresuelvoTheme {
                ProviderProposalScreen(ProposalUiState.Form(42, 7, "Ana Pérez"),
                    {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag(PROPOSAL_DURATION_TAG).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dateAndTimeOpenNativePickers() {
        compose.setContent {
            LoresuelvoTheme {
                ProviderProposalScreen(ProposalUiState.Form(42, 7, "Ana Pérez"),
                    {}, {}, {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag(PROPOSAL_DATE_TAG).performClick()
        assertTrue(ShadowDialog.getLatestDialog() is android.app.DatePickerDialog)
        ShadowDialog.getLatestDialog().dismiss()
        compose.onNodeWithTag(PROPOSAL_TIME_TAG).performClick()
        assertTrue(ShadowDialog.getLatestDialog() is android.app.TimePickerDialog)
        ShadowDialog.getLatestDialog().dismiss()
    }
}
