package com.loresuelvo.serviceprovider.ui.screens.conversation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.loresuelvo.serviceprovider.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProposalScheduleFields(date: String, time: String, onDate: (String) -> Unit,
    onTime: (String) -> Unit, enabled: Boolean) {
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val selectedDate = runCatching { LocalDate.parse(date) }.getOrNull()
    val selectedTime = runCatching { LocalTime.parse(time) }.getOrNull()
    ScheduleField(stringResource(R.string.provider_proposal_date),
        selectedDate?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
            ?: stringResource(R.string.provider_proposal_choose_date),
        Icons.Outlined.CalendarMonth, PROPOSAL_DATE_TAG, enabled) { showDate = true }
    ScheduleField(stringResource(R.string.provider_proposal_time),
        selectedTime?.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
            ?: stringResource(R.string.provider_proposal_choose_time),
        Icons.Outlined.Schedule, PROPOSAL_TIME_TAG, enabled) { showTime = true }
    if (showDate && enabled) {
        // Material date pickers represent calendar dates at midnight UTC, independently of the visit zone.
        val state = rememberDatePickerState(initialSelectedDateMillis =
            selectedDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let {
                    onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                showDate = false
            }) { Text(stringResource(R.string.provider_form_apply)) } },
            dismissButton = { TextButton(onClick = { showDate = false }) {
                Text(stringResource(R.string.provider_completion_cancel))
            } }) { DatePicker(state, modifier = Modifier.testTag("proposal-date-picker")) }
    }
    if (showTime && enabled) {
        val now = LocalTime.now()
        val state = rememberTimePickerState(initialHour = selectedTime?.hour ?: now.hour,
            initialMinute = selectedTime?.minute ?: now.minute,
            is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        var keyboard by rememberSaveable { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { showTime = false },
            title = { Text(stringResource(R.string.provider_proposal_choose_time)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (keyboard) TimeInput(state) else TimePicker(state)
                    TextButton(onClick = { keyboard = !keyboard }) {
                        Text(stringResource(if (keyboard) R.string.provider_proposal_use_clock
                            else R.string.provider_proposal_type_time))
                    }
                }
            }, confirmButton = { TextButton(onClick = {
                onTime(String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute))
                showTime = false
            }) { Text(stringResource(R.string.provider_form_apply)) } },
            dismissButton = { TextButton(onClick = { showTime = false }) {
                Text(stringResource(R.string.provider_completion_cancel))
            } })
    }
}

@Composable
private fun ScheduleField(label: String, value: String, icon: ImageVector, tag: String,
    enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag(tag)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
