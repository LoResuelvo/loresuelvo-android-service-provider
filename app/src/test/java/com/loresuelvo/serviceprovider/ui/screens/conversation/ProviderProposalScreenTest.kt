package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
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
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import com.loresuelvo.serviceprovider.domain.proposal.ValidatedServiceProposal
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

    @Test fun invalidAmountShowsLocalizedCorrectionAndContinueAction() {
        var continued = false
        val form = mutableStateOf(ProposalUiState.Form(42, 7, "Ana Pérez",
            errors = setOf(ProposalValidationError.Amount)))
        compose.setContent {
            LoresuelvoTheme {
                ProviderProposalScreen(
                    form.value,
                    { form.value = form.value.copy(amount = it, errors = emptySet()) },
                    {}, {}, {}, {}, {}, {}, onContinue = { continued = true },
                )
            }
        }
        compose.onNodeWithText("Ingresá un monto positivo en pesos, con hasta dos decimales y sin separadores de miles.")
            .assertExists()
        compose.onNodeWithText("Monto").performTextInput("100")
        compose.onNodeWithText("Ingresá un monto positivo en pesos, con hasta dos decimales y sin separadores de miles.")
            .assertDoesNotExist()
        compose.onNodeWithTag(PROPOSAL_CONTINUE_TAG).performScrollTo().performClick()
        assertTrue(continued)
    }

    @Test fun ambiguousTimeShowsZoneAndSelectedOffset() {
        val form = mutableStateOf(ProposalUiState.Form(42, 7, "Ana Pérez",
            zoneId = "Australia/Lord_Howe",
            errors = setOf(ProposalValidationError.AmbiguousTime(listOf(630, 660)))))
        compose.setContent {
            LoresuelvoTheme {
                ProviderProposalScreen(form.value, {}, {}, {}, {}, {}, {}, {},
                    onOffsetSelect = { form.value = form.value.copy(selectedOffsetMinutes = it) })
            }
        }
        compose.onNodeWithText("Zona horaria: Australia/Lord_Howe").assertExists()
        compose.onNodeWithTag("${PROPOSAL_OFFSET_TAG_PREFIX}660").performScrollTo().performClick().assertIsSelected()
        assertEquals(660, form.value.selectedOffsetMinutes)
    }

    @Test fun reviewDisplaysVisitDetailsWithoutEnabledSend() {
        val review = ProposalUiState.Reviewing(
            ProposalUiState.Form(42, 7, "Ana Pérez", date = "2026-10-01", time = "10:00", zoneId = "UTC"),
            ValidatedServiceProposal(7, "100.5", 0L, 0, "Inspect sink", 45),
        )
        compose.setContent { LoresuelvoTheme { ProviderProposalConfirmationDialog(review, {}) } }
        compose.onNodeWithTag(PROPOSAL_CONFIRMATION_TAG).assertExists()
        listOf("Consumidor: Ana Pérez", "Monto: ARS 100.5", "Visita: 2026-10-01 a las 10:00 (UTC, UTC+00:00)",
            "45 minutos", "Inspect sink").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Confirmar envío").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "es-rAR-w400dp-h400dp")
    fun longReviewScrollsAtLargeFontWithActionsReachable() {
        var returnedToEditing = false
        val reason = "Inspect every sink and pipe. ".repeat(50)
        val review = ProposalUiState.Reviewing(
            ProposalUiState.Form(42, 7, "Ana Pérez", date = "2026-10-01", time = "10:00"),
            ValidatedServiceProposal(7, "100", 0L, 0, reason, 45),
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                LoresuelvoTheme { ProviderProposalConfirmationDialog(review, { returnedToEditing = true }) }
            }
        }
        compose.onNodeWithText(reason).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Volver a editar").assertIsDisplayed().performClick()
        assertTrue(returnedToEditing)
        compose.onNodeWithText("Confirmar envío").assertIsNotEnabled()
    }

    @Test fun cancelButtonAndBackInvokeReviewCancellation() {
        var cancellations = 0
        val review = ProposalUiState.Reviewing(
            ProposalUiState.Form(42, 7, "Ana Pérez"),
            ValidatedServiceProposal(7, "100", 0L, 0, "Inspect sink", 45),
        )
        compose.setContent {
            LoresuelvoTheme { ProviderProposalConfirmationDialog(review) { cancellations++ } }
        }
        compose.onNodeWithText("Volver a editar").performClick()
        assertEquals(1, cancellations)
        ShadowDialog.getLatestDialog().onBackPressed()
        assertEquals(2, cancellations)
    }
}
