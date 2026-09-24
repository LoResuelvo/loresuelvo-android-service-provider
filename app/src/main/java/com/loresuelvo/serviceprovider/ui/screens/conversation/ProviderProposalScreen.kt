package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import com.loresuelvo.serviceprovider.domain.proposal.ProposalValidationError
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import java.util.Calendar
import java.util.Locale

private val durations = listOf(15, 30, 45, 60, 90, 120, 150, 180, 240, 300, 360, 480)

// Cohesion exception: state and seven field/back callbacks belong to one editable form.
// The form stays together until review/submit behavior warrants a separate fields section.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderProposalScreen(
    form: ProposalUiState.Form,
    onAmountChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onReasonChange: (String) -> Unit,
    onDurationSelect: (Int?) -> Unit,
    onCustomDurationChange: (String) -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit = {},
    onOffsetSelect: (Int) -> Unit = {},
    enabled: Boolean = true,
) {
    var durationExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(modifier = if (maxWidth < 600.dp) Modifier.fillMaxSize() else Modifier.width(560.dp).fillMaxHeight()) {
            Scaffold(
                modifier = Modifier.testTag(PROPOSAL_FORM_TAG),
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.provider_proposal_title)) },
                        navigationIcon = {
                            IconButton(onClick = onClose, enabled = enabled) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.provider_proposal_close))
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                        .padding(padding).padding(horizontal = 16.dp)
                        .testTag(PROPOSAL_FIELDS_TAG),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.provider_proposal_consumer, form.consumerName))
                        if (form.submissionUncertain) Text(stringResource(R.string.provider_proposal_uncertain))
                        OutlinedTextField(
                            value = form.amount,
                            onValueChange = onAmountChange,
                            enabled = enabled,
                            label = { Text(stringResource(R.string.provider_proposal_amount)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = ProposalValidationError.Amount in form.errors,
                            supportingText = { FieldError(form.errors, ProposalValidationError.Amount, R.string.provider_proposal_amount_error) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(
                            enabled = enabled,
                            onClick = {
                                val now = Calendar.getInstance()
                                val selected = form.date.split("-").mapNotNull(String::toIntOrNull)
                                val year = selected.getOrNull(0) ?: now.get(Calendar.YEAR)
                                val month = selected.getOrNull(1)?.minus(1) ?: now.get(Calendar.MONTH)
                                val day = selected.getOrNull(2) ?: now.get(Calendar.DAY_OF_MONTH)
                                android.app.DatePickerDialog(context, { _, year, month, day ->
                                    onDateChange(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day))
                                }, year, month, day).show()
                            },
                            modifier = Modifier.fillMaxWidth().testTag(PROPOSAL_DATE_TAG),
                        ) { Text(stringResource(R.string.provider_proposal_date) + ": " + form.date) }
                        FieldError(form.errors, ProposalValidationError.Date, R.string.provider_proposal_date_error)
                        OutlinedButton(
                            enabled = enabled,
                            onClick = {
                                val now = Calendar.getInstance()
                                val selected = form.time.split(":").mapNotNull(String::toIntOrNull)
                                val hour = selected.getOrNull(0) ?: now.get(Calendar.HOUR_OF_DAY)
                                val minute = selected.getOrNull(1) ?: now.get(Calendar.MINUTE)
                                android.app.TimePickerDialog(context, { _, hour, minute ->
                                    onTimeChange(String.format(Locale.ROOT, "%02d:%02d", hour, minute))
                                }, hour, minute,
                                    android.text.format.DateFormat.is24HourFormat(context)).show()
                            },
                            modifier = Modifier.fillMaxWidth().testTag(PROPOSAL_TIME_TAG),
                        ) { Text(stringResource(R.string.provider_proposal_time) + ": " + form.time) }
                        FieldError(form.errors, ProposalValidationError.Time, R.string.provider_proposal_time_error)
                        FieldError(form.errors, ProposalValidationError.LeadTime, R.string.provider_proposal_lead_time_error)
                        val ambiguity = form.errors.filterIsInstance<ProposalValidationError.AmbiguousTime>().firstOrNull()
                        if (ambiguity != null) {
                            Text(stringResource(R.string.provider_proposal_ambiguous_time_error))
                            Text(stringResource(R.string.provider_proposal_zone, form.zoneId))
                            Column(modifier = Modifier.selectableGroup()) {
                                ambiguity.offsetsMinutes.forEach { minutes ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                            .selectable(
                                                selected = form.selectedOffsetMinutes == minutes,
                                                onClick = { onOffsetSelect(minutes) },
                                                enabled = enabled,
                                                role = Role.RadioButton,
                                            ).testTag("$PROPOSAL_OFFSET_TAG_PREFIX$minutes"),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RadioButton(
                                            selected = form.selectedOffsetMinutes == minutes,
                                            onClick = null,
                                        )
                                        Text(stringResource(R.string.provider_proposal_offset_choice, formatOffset(minutes)))
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = form.reason,
                            onValueChange = onReasonChange,
                            enabled = enabled,
                            label = { Text(stringResource(R.string.provider_proposal_reason)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            isError = ProposalValidationError.Reason in form.errors,
                            supportingText = { FieldError(form.errors, ProposalValidationError.Reason, R.string.provider_proposal_reason_error) },
                        )
                        ExposedDropdownMenuBox(expanded = durationExpanded && enabled,
                            onExpandedChange = { if (enabled) durationExpanded = it }) {
                            OutlinedTextField(
                                value = if (form.customDuration) stringResource(R.string.provider_proposal_duration_custom) else form.duration,
                                onValueChange = {},
                                readOnly = true,
                                enabled = enabled,
                                label = { Text(stringResource(R.string.provider_proposal_duration)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = durationExpanded) },
                                isError = ProposalValidationError.Duration in form.errors,
                                supportingText = { FieldError(form.errors, ProposalValidationError.Duration, R.string.provider_proposal_duration_error) },
                                modifier = Modifier.fillMaxWidth().menuAnchor().testTag(PROPOSAL_DURATION_TAG),
                            )
                            ExposedDropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                                durations.forEach { minutes ->
                                    DropdownMenuItem(
                                        enabled = enabled,
                                        text = { Text(stringResource(R.string.provider_proposal_duration_minutes, minutes)) },
                                        onClick = { onDurationSelect(minutes); durationExpanded = false },
                                    )
                                }
                                DropdownMenuItem(
                                    enabled = enabled,
                                    text = { Text(stringResource(R.string.provider_proposal_duration_custom)) },
                                    onClick = { onDurationSelect(null); durationExpanded = false },
                                )
                            }
                        }
                        if (form.customDuration) {
                            OutlinedTextField(
                                value = form.duration,
                                onValueChange = onCustomDurationChange,
                                enabled = enabled,
                                label = { Text(stringResource(R.string.provider_proposal_duration_minutes_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag(PROPOSAL_CUSTOM_DURATION_TAG),
                            )
                        }
                        Button(onClick = onContinue, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().testTag(PROPOSAL_CONTINUE_TAG)) {
                            Text(stringResource(R.string.provider_proposal_continue))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldError(errors: Set<ProposalValidationError>, error: ProposalValidationError, message: Int) {
    if (error in errors) Text(stringResource(message))
}

internal fun formatOffset(minutes: Int): String =
    String.format(Locale.ROOT, "UTC%s%02d:%02d", if (minutes < 0) "-" else "+",
        kotlin.math.abs(minutes) / 60, kotlin.math.abs(minutes) % 60)


const val PROPOSAL_FORM_TAG = "provider-proposal-form"
const val PROPOSAL_FIELDS_TAG = "provider-proposal-fields"
const val PROPOSAL_DURATION_TAG = "provider-proposal-duration"
const val PROPOSAL_DATE_TAG = "provider-proposal-date"
const val PROPOSAL_TIME_TAG = "provider-proposal-time"
const val PROPOSAL_CUSTOM_DURATION_TAG = "provider-proposal-custom-duration"
const val PROPOSAL_CONTINUE_TAG = "provider-proposal-continue"
const val PROPOSAL_OFFSET_TAG_PREFIX = "provider-proposal-offset-"
const val PROPOSAL_CONFIRMATION_TAG = "provider-proposal-confirmation"
