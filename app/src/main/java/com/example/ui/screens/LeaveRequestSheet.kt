package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AiParseState
import com.example.domain.LeaveRequest
import com.example.domain.LeaveType
import com.example.domain.LeaveValidationEngine
import com.example.domain.Member
import com.example.ui.theme.AiAccentContainer
import com.example.ui.theme.AiAccentContainerDark
import com.example.ui.theme.AiAccentForeground
import com.example.ui.theme.AiAccentForegroundDark
import com.example.ui.theme.JetBrainsMonoFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LeaveRequestSheet(
    member: Member,
    existingRequests: List<LeaveRequest>,
    aiParseState: AiParseState,
    isSubmitting: Boolean,
    onParseWithAi: (String) -> Unit,
    onResetAiState: () -> Unit,
    onSubmit: (LeaveType, String, String, String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LeaveValidationEngine.todayIsoDate() }
    val defaultStart = remember { LeaveValidationEngine.addDaysToIsoDate(today, 1) }

    var aiPhrase by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(LeaveType.ANNUAL) }
    var startDate by remember { mutableStateOf(defaultStart) }
    var endDate by remember { mutableStateOf(defaultStart) }
    var reason by remember { mutableStateOf("") }
    var aiAssisted by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Apply AI suggestions into editable fields (employee always verifies before submitting)
    LaunchedEffect(aiParseState) {
        when (aiParseState) {
            is AiParseState.Suggested -> {
                selectedType = aiParseState.leaveType
                startDate = aiParseState.startDate
                endDate = aiParseState.endDate
                aiAssisted = true
            }
            is AiParseState.NeedsClarification -> {
                aiParseState.partialLeaveType?.let { selectedType = it }
                aiParseState.partialStartDate?.let { startDate = it }
                aiParseState.partialEndDate?.let { endDate = it }
            }
            else -> Unit
        }
    }

    val dayCount = LeaveValidationEngine.inclusiveDayCount(startDate, endDate)
    val validationError = LeaveValidationEngine.validateSubmission(
        member = member,
        leaveType = selectedType,
        startDate = startDate,
        endDate = endDate,
        existingRequests = existingRequests
    )

    val dark = isSystemInDarkTheme()
    val aiBg = if (dark) AiAccentContainerDark else AiAccentContainer
    val aiFg = if (dark) AiAccentForegroundDark else AiAccentForeground

    ModalBottomSheet(
        onDismissRequest = {
            onResetAiState()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "New Leave Request",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            // One step where AI helps: Natural-language leave entry
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = aiBg),
                border = BorderStroke(1.dp, aiFg.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = stringResource(R.string.cd_ai_sparkle),
                            tint = aiFg,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "AI Smart Fill (Employee Verifies)",
                            style = MaterialTheme.typography.titleSmall,
                            color = aiFg
                        )
                    }

                    OutlinedTextField(
                        value = aiPhrase,
                        onValueChange = { aiPhrase = it },
                        placeholder = {
                            Text("e.g. Annual leave on 23 and 24 November 2026")
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_phrase_input")
                    )

                    Button(
                        onClick = { onParseWithAi(aiPhrase) },
                        enabled = aiParseState !is AiParseState.Loading && aiPhrase.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("ai_suggest_button")
                    ) {
                        if (aiParseState is AiParseState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Extracting leave type & dates...")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_suggest_ai))
                        }
                    }

                    AnimatedVisibility(visible = aiParseState !is AiParseState.Idle && aiParseState !is AiParseState.Loading) {
                        when (aiParseState) {
                            is AiParseState.Suggested -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${aiParseState.summaryNote} Review the fields below before submitting.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            is AiParseState.NeedsClarification -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = aiParseState.promptMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            is AiParseState.Error -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = aiParseState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            else -> Unit
                        }
                    }
                }
            }

            // Leave Type Selector
            Text(
                text = "Leave Category",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LeaveType.entries.forEach { type ->
                    val balText = if (type.isPaidTracked) {
                        "${type.label} (${member.availableDaysFor(type)}d)"
                    } else {
                        type.label
                    }
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(balText) },
                        modifier = Modifier.testTag("leave_type_${type.name.lowercase()}")
                    )
                }
            }

            // Date Selection Row
            Text(
                text = "Leave Dates",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it.take(10) },
                    label = { Text("Start (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_date_input")
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it.take(10) },
                    label = { Text("End (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("end_date_input")
                )
            }
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_date_picker_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Pick Dates on Calendar")
            }

            // Live Balance Impact Summary
            if (dayCount > 0) {
                val available = member.availableDaysFor(selectedType)
                val preview = if (selectedType.isPaidTracked) {
                    "$dayCount day(s) · Balance: ${available}d → ${(available - dayCount).coerceAtLeast(0)}d if approved"
                } else {
                    "$dayCount day(s) of Unpaid leave (logged for HR reporting)"
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Real-time Validation Error Banner (Overlaps / Inverted Dates / Low Balance)
            if (validationError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("request_validation_error")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = "Validation error",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = validationError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it.take(500) },
                label = { Text("Note for Manager (optional)") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("leave_reason_input")
            )

            Button(
                onClick = { onSubmit(selectedType, startDate, endDate, reason, aiAssisted) },
                enabled = validationError == null && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_leave_request_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.action_submit_request))
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val s = rangeState.selectedStartDateMillis
                        val e = rangeState.selectedEndDateMillis ?: s
                        if (s != null) {
                            startDate = LeaveValidationEngine.epochMillisToIsoDate(s)
                            endDate = LeaveValidationEngine.epochMillisToIsoDate(e ?: s)
                        }
                        showDatePicker = false
                    }
                ) { Text("Apply Dates") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(
                state = rangeState,
                modifier = Modifier.height(460.dp)
            )
        }
    }
}
