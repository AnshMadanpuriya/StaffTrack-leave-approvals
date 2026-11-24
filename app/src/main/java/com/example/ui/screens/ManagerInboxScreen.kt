package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.domain.LeaveRequest
import com.example.domain.LeaveValidationEngine
import com.example.domain.Member
import com.example.domain.RequestStatus
import com.example.domain.Team
import com.example.ui.components.AiBadge
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun ManagerInboxScreen(
    currentMember: Member,
    teams: List<Team>,
    members: List<Member>,
    allRequests: List<LeaveRequest>,
    isBusy: Boolean,
    onApprove: (requestId: String, comment: String) -> Unit,
    onDecline: (requestId: String, comment: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val authorizedTeams = remember(currentMember, teams) {
        teams.filter { LeaveValidationEngine.canDecideForTeam(currentMember, it.id) }
    }
    var selectedTeamId by remember { mutableStateOf<String?>(null) }

    val authorizedRequests = remember(allRequests, currentMember, selectedTeamId) {
        allRequests.filter { req ->
            LeaveValidationEngine.canDecideForTeam(currentMember, req.teamId) &&
                (selectedTeamId == null || req.teamId == selectedTeamId)
        }
    }
    val pendingRequests = authorizedRequests.filter { it.parsedStatus == RequestStatus.PENDING }
    val recentDecided = authorizedRequests.filter { it.parsedStatus != RequestStatus.PENDING }.take(15)
    val membersByUid = remember(members) { members.associateBy { it.uid } }

    var pendingDecision by remember { mutableStateOf<Pair<LeaveRequest, Boolean>?>(null) } // request to isApprove
    var decisionComment by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Manager Approval Inbox",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Every approval atomically locks balance, rechecks pending status, writes audit history, and notifies the employee.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (authorizedTeams.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Lock,
                    title = "No team approval rights assigned",
                    subtitle = "Ask an HR administrator in Org & Roles to assign you as a Manager for one or more teams."
                )
            }
            return@LazyColumn
        }

        // Team filter chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedTeamId == null,
                        onClick = { selectedTeamId = null },
                        label = { Text("All Teams (${authorizedTeams.size})") }
                    )
                }
                items(authorizedTeams, key = { it.id }) { team ->
                    FilterChip(
                        selected = selectedTeamId == team.id,
                        onClick = { selectedTeamId = team.id },
                        label = { Text(team.name) }
                    )
                }
            }
        }

        item {
            Text(
                text = "Pending Decisions (${pendingRequests.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (pendingRequests.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Inbox,
                    title = "All caught up",
                    subtitle = "No pending leave requests awaiting your review in the selected team(s)."
                )
            }
        } else {
            items(pendingRequests, key = { it.id }) { req ->
                val emp = membersByUid[req.employeeUid]
                PendingApprovalCard(
                    request = req,
                    employee = emp,
                    isBusy = isBusy,
                    onApproveClick = {
                        decisionComment = ""
                        pendingDecision = req to true
                    },
                    onDeclineClick = {
                        decisionComment = ""
                        pendingDecision = req to false
                    }
                )
            }
        }

        if (recentDecided.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Recently Decided",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(recentDecided, key = { "decided_${it.id}" }) { req ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${req.employeeName} · ${req.parsedType.label} (${req.dayCount}d)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${req.startDate} to ${req.endDate} · ${req.teamName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusPill(status = req.parsedStatus)
                    }
                }
            }
        }
    }

    val decisionTarget = pendingDecision
    if (decisionTarget != null) {
        val (req, isApprove) = decisionTarget
        AlertDialog(
            onDismissRequest = { pendingDecision = null },
            title = {
                Text(if (isApprove) "Approve Leave Request" else "Decline Leave Request")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${req.employeeName} · ${req.parsedType.label} (${req.dayCount}d: ${req.startDate} to ${req.endDate})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = decisionComment,
                        onValueChange = { decisionComment = it.take(500) },
                        label = {
                            Text(if (isApprove) "Comment for employee (optional)" else "Reason for declining")
                        },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("decision_comment_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isApprove) onApprove(req.id, decisionComment)
                        else onDecline(req.id, decisionComment)
                        pendingDecision = null
                    },
                    colors = if (isApprove) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    },
                    modifier = Modifier.testTag("confirm_decision_button")
                ) {
                    Text(if (isApprove) stringResource(R.string.action_approve) else stringResource(R.string.action_decline))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDecision = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PendingApprovalCard(
    request: LeaveRequest,
    employee: Member?,
    isBusy: Boolean,
    onApproveClick: () -> Unit,
    onDeclineClick: () -> Unit
) {
    val available = employee?.availableDaysFor(request.parsedType)
    val lowBalance = request.parsedType.isPaidTracked && available != null && available < request.dayCount

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = request.employeeName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${request.teamName} · ${request.parsedType.label} Leave",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (request.aiAssisted) {
                    AiBadge()
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${LeaveValidationEngine.formatDisplayDate(request.startDate)} → ${LeaveValidationEngine.formatDisplayDate(request.endDate)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${request.dayCount}d",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (available != null) {
                Surface(
                    color = if (lowBalance) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (request.parsedType.isPaidTracked) {
                            if (lowBalance) {
                                "Low balance warning: ${available}d left vs ${request.dayCount}d requested (transaction will hold Pending)"
                            } else {
                                "Current ${request.parsedType.label} balance: ${available}d → ${available - request.dayCount}d after approval"
                            }
                        } else {
                            "Unpaid leave (${available}d used so far)"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = if (lowBalance) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (request.reason.isNotBlank()) {
                Text(
                    text = "Note: ${request.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDeclineClick,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("decline_button_${request.id}")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_decline))
                }
                Button(
                    onClick = onApproveClick,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("approve_button_${request.id}")
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_approve))
                }
            }
        }
    }
}
