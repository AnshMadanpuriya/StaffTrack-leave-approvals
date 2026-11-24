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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.LeaveType
import com.example.domain.Member
import com.example.domain.Team
import com.example.domain.UserRole
import com.example.domain.VerificationStatus
import com.example.domain.VerificationTestCase
import com.example.domain.Workspace
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.RoleBadge
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun HrOrgAdminSection(
    workspace: Workspace,
    currentMember: Member,
    teams: List<Team>,
    members: List<Member>,
    isBusy: Boolean,
    onCreateTeam: (String, String) -> Unit,
    onUpdateMemberRoleAndTeams: (String, UserRole, Team, List<String>) -> Unit,
    onAdjustBalance: (String, LeaveType, Int, String) -> Unit,
    onCopyInviteCode: (String) -> Unit
) {
    val isHr = currentMember.parsedRole == UserRole.HR
    var showNewTeamDialog by remember { mutableStateOf(false) }
    var editingRoleMember by remember { mutableStateOf<Member?>(null) }
    var adjustingBalanceMember by remember { mutableStateOf<Member?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Workspace Invite Code Card
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${workspace.name} · Invite Code",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = workspace.inviteCode,
                            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFamily),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Share this code with colleagues to join as Employee.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    IconButton(
                        onClick = { onCopyInviteCode(workspace.inviteCode) },
                        modifier = Modifier.testTag("copy_invite_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = stringResource(R.string.cd_copy_invite),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Teams Header
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Teams (${teams.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (isHr) {
                    OutlinedButton(
                        onClick = { showNewTeamDialog = true },
                        modifier = Modifier.testTag("add_team_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Team")
                    }
                }
            }
        }

        items(teams, key = { it.id }) { team ->
            val teamMemberCount = members.count { it.teamId == team.id }
            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = team.code,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = team.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "$teamMemberCount member(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Members & Roles
        item {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Workspace Members (${members.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(members, key = { it.uid }) { member ->
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = member.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = member.teamName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RoleBadge(role = member.parsedRole)
                    }

                    Text(
                        text = "Balances · Annual ${member.annualDays}d · Sick ${member.sickDays}d · Personal ${member.personalDays}d · Unpaid ${member.unpaidUsedDays}d",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isHr) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { editingRoleMember = member },
                                enabled = !isBusy && teams.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("edit_role_${member.uid}")
                            ) {
                                Text("Role & Teams")
                            }
                            OutlinedButton(
                                onClick = { adjustingBalanceMember = member },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("adjust_balance_${member.uid}")
                            ) {
                                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Adjust Balance")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewTeamDialog) {
        var teamName by remember { mutableStateOf("") }
        var teamCode by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewTeamDialog = false },
            title = { Text("Create Team") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = teamName,
                        onValueChange = { teamName = it.take(80) },
                        label = { Text("Team Name (e.g. Assay Automation)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = teamCode,
                        onValueChange = { teamCode = it.uppercase().take(12) },
                        label = { Text("Short Code (e.g. ASSAY)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateTeam(teamName, teamCode)
                        showNewTeamDialog = false
                    },
                    enabled = teamName.trim().length >= 2 && teamCode.trim().length >= 2
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewTeamDialog = false }) { Text("Cancel") }
            }
        )
    }

    val targetRoleMember = editingRoleMember
    if (targetRoleMember != null && teams.isNotEmpty()) {
        var selectedRole by remember(targetRoleMember) { mutableStateOf(targetRoleMember.parsedRole) }
        var selectedTeam by remember(targetRoleMember) {
            mutableStateOf(teams.firstOrNull { it.id == targetRoleMember.teamId } ?: teams.first())
        }
        val managedIds = remember(targetRoleMember) {
            mutableStateListOf<String>().apply { addAll(targetRoleMember.managedTeamIds) }
        }

        AlertDialog(
            onDismissRequest = { editingRoleMember = null },
            title = { Text("Edit ${targetRoleMember.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Role", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        UserRole.entries.forEach { role ->
                            FilterChip(
                                selected = selectedRole == role,
                                onClick = { selectedRole = role },
                                label = { Text(role.label) }
                            )
                        }
                    }
                    Text("Home Team", style = MaterialTheme.typography.titleSmall)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        teams.forEach { t ->
                            FilterChip(
                                selected = selectedTeam.id == t.id,
                                onClick = { selectedTeam = t },
                                label = { Text(t.name) }
                            )
                        }
                    }
                    if (selectedRole != UserRole.EMPLOYEE) {
                        Text("Approval Rights (Managed Teams)", style = MaterialTheme.typography.titleSmall)
                        teams.forEach { t ->
                            val checked = t.id in managedIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked && t.id !in managedIds) managedIds.add(t.id)
                                        if (!isChecked) managedIds.remove(t.id)
                                    }
                                )
                                Text(t.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateMemberRoleAndTeams(targetRoleMember.uid, selectedRole, selectedTeam, managedIds.toList())
                    editingRoleMember = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingRoleMember = null }) { Text("Cancel") }
            }
        )
    }

    val targetBalMember = adjustingBalanceMember
    if (targetBalMember != null) {
        var selectedLeaveType by remember(targetBalMember) { mutableStateOf(LeaveType.ANNUAL) }
        var balanceText by remember(targetBalMember, selectedLeaveType) {
            mutableStateOf(targetBalMember.availableDaysFor(selectedLeaveType).toString())
        }
        var reason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { adjustingBalanceMember = null },
            title = { Text("Audited Balance Adjustment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Adjusting ${targetBalMember.displayName} writes an immutable HR audit record.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(LeaveType.ANNUAL, LeaveType.SICK, LeaveType.PERSONAL).forEach { lt ->
                            FilterChip(
                                selected = selectedLeaveType == lt,
                                onClick = {
                                    selectedLeaveType = lt
                                    balanceText = targetBalMember.availableDaysFor(lt).toString()
                                },
                                label = { Text(lt.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = balanceText,
                        onValueChange = { balanceText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("New ${selectedLeaveType.label} Balance (days)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(500) },
                        label = { Text("Reason for adjustment") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                val parsed = balanceText.toIntOrNull()
                Button(
                    onClick = {
                        if (parsed != null) {
                            onAdjustBalance(targetBalMember.uid, selectedLeaveType, parsed, reason)
                            adjustingBalanceMember = null
                        }
                    },
                    enabled = parsed != null && parsed in 0..365
                ) { Text("Save & Log") }
            },
            dismissButton = {
                TextButton(onClick = { adjustingBalanceMember = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun HrVerificationSuiteSection(
    currentMember: Member,
    cases: List<VerificationTestCase>,
    isRunning: Boolean,
    onRunAllTests: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
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
                    Text(
                        text = "Transaction & Concurrency Verification Suite",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Executes all 5 StrainX Bioworks proposal verification tests live against Firestore atomic transactions using isolated synthetic records (automatically cleaned up afterward).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (currentMember.parsedRole != UserRole.HR) {
                        EmptyStateCard(
                            icon = Icons.Rounded.Lock,
                            title = "HR Admin role required",
                            subtitle = "Sign in as an HR Administrator to run the live Firestore transaction test harness."
                        )
                    } else {
                        Button(
                            onClick = onRunAllTests,
                            enabled = !isRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("run_verification_suite_button")
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("Running Live Transaction Tests...")
                            } else {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_run_tests))
                            }
                        }
                    }
                }
            }
        }

        items(cases, key = { it.id }) { testCase ->
            val (icon, tint) = when (testCase.status) {
                VerificationStatus.PASSED -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.tertiary
                VerificationStatus.FAILED -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
                VerificationStatus.RUNNING -> Icons.Rounded.PlayArrow to MaterialTheme.colorScheme.primary
                VerificationStatus.IDLE -> Icons.Rounded.RadioButtonUnchecked to MaterialTheme.colorScheme.outline
            }
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (testCase.status == VerificationStatus.RUNNING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = testCase.status.name,
                                tint = tint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = testCase.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = testCase.scenario,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = testCase.detail,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                            color = tint,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
