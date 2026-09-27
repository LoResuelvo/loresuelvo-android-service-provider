package com.loresuelvo.serviceprovider.ui.screens.turns

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.loresuelvo.serviceprovider.domain.activity.WorkOrder
import com.loresuelvo.serviceprovider.domain.activity.EvidenceImagePreparation
import com.loresuelvo.serviceprovider.domain.activity.PreparedEvidenceImage
import com.loresuelvo.serviceprovider.domain.activity.WorkOrderStatus
import com.loresuelvo.serviceprovider.domain.usecase.activity.CompletionDraftValidation
import com.loresuelvo.serviceprovider.ui.theme.LoresuelvoTheme
import com.loresuelvo.serviceprovider.ui.turns.CompletionEvidenceSelection
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionIssue
import com.loresuelvo.serviceprovider.ui.turns.EvidenceSelectionStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "es-rAR")
class ProviderCompletionFormScreenTest {
    @get:Rule val compose = createComposeRule()
    private val order = WorkOrder(7, "Ana Pérez", "Reparar la canilla", 1, WorkOrderStatus.Scheduled)

    @Test fun eligible_order_shows_identity_and_empty_form_without_submission() {
        var backs = 0
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionFormScreen(order, CompletionFormAvailability.Eligible, "", {}, {}, { backs++ })
        } }

        compose.onNodeWithText("Ana Pérez").assertExists()
        compose.onNodeWithText("Reparar la canilla").assertExists()
        compose.onNodeWithText("Descripción de la entrega").assertExists()
        compose.onNodeWithText("Agregar fotos").assertExists()
        compose.onNodeWithTag("completion_submit").assertIsNotEnabled()
        compose.onNodeWithText("Volver").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun checking_and_ineligible_orders_do_not_show_form() {
        var availability by mutableStateOf(CompletionFormAvailability.Checking)
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionFormScreen(order, availability, "", {}, {}, {})
        } }
        val explanations = mapOf(
            CompletionFormAvailability.Checking to "Consultando el estado de la orden…",
            CompletionFormAvailability.TooEarly to "Todavía no podés informar la finalización de este turno.",
            CompletionFormAvailability.AlreadyReported to "Esta orden ya tiene un reporte de finalización.",
            CompletionFormAvailability.Forbidden to "No tenés permiso para informar la finalización de esta orden.",
        )
        for ((state, explanation) in explanations) {
            compose.runOnIdle { availability = state }
            compose.onNodeWithText(explanation).assertExists()
            compose.onNodeWithText("Descripción de la entrega").assertDoesNotExist()
            compose.onNodeWithTag("completion_submit").assertDoesNotExist()
        }
    }

    @Test fun evidence_rows_keep_order_and_expose_remove_add_and_typed_errors() {
        var removed: Long? = null
        var additions = 0
        val selections = listOf(
            CompletionEvidenceSelection(11, "one", EvidenceSelectionStatus.Ready(
                PreparedEvidenceImage("one.jpg", "image/jpeg", 100, "/missing/one.jpg"))),
            CompletionEvidenceSelection(12, "two", EvidenceSelectionStatus.Preparing),
            CompletionEvidenceSelection(13, "three", EvidenceSelectionStatus.Invalid(
                EvidenceImagePreparation.Invalid.ExceedsMaxSize)),
        )
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionFormScreen(order, CompletionFormAvailability.Eligible, "Trabajo terminado", {},
                { additions++ }, {}, canAddPhotos = true, evidence = selections,
                evidenceIssue = EvidenceSelectionIssue.MaximumReached, onRemoveEvidence = { removed = it })
        } }

        compose.onNodeWithTag("completion_description").assertExists()
        compose.onNodeWithText("Trabajo terminado").assertExists()
        compose.onNodeWithTag("completion_evidence_0_11").assertExists()
        compose.onNodeWithTag("completion_evidence_1_12").assertExists()
        compose.onNodeWithTag("completion_evidence_2_13").assertExists()
        compose.onNodeWithText("Preparando foto…").assertExists()
        compose.onNodeWithText("La foto supera 5 MiB.").assertExists()
        compose.onNodeWithText("No podés agregar más de 3 fotos.").assertExists()
        compose.onNodeWithTag("completion_evidence_1_12").performScrollTo()
        compose.onNodeWithContentDescription("Quitar foto 2").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Agregar fotos").performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle {
            assertEquals(12L, removed)
            assertEquals(1, additions)
        }
    }

    @Test fun duplicate_confirmed_ids_show_localized_explanation_without_hiding_draft() {
        compose.setContent { LoresuelvoTheme {
            ProviderCompletionFormScreen(order, CompletionFormAvailability.Eligible,
                "Trabajo terminado", {}, {}, {},
                validationIssue = CompletionDraftValidation.Invalid.DuplicatePhotoIds)
        } }

        compose.onNodeWithText("Hay fotografías repetidas. Quitá una para continuar.").assertExists()
        compose.onNodeWithText("Trabajo terminado").assertExists()
        compose.onNodeWithTag("completion_description").assertExists()
    }
}
