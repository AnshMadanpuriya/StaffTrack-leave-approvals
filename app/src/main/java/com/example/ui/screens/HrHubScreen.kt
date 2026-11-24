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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.LocalSqlStats
import com.example.domain.AuditAction
import com.example.domain.AuditLog
import com.example.domain.LeaveRequest
import com.example.domain.LeaveType
import com.example.domain.LeaveValidationEngine
import com.example.domain.Member
import com.example.domain.RequestStatus
import com.example.domain.Team
import com.example.domain.UserRole
import com.example.domain.VerificationTestCase
import com.example.domain.Workspace
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiMetricTile
import com.example.ui.components.RoleBadge
import com.example.ui.components.SqlSyncStatusCard
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.viewmodel.HrSubTab

@Composable
fun HrHubScreen(
    workspace: Workspace,
    currentMember: Member,
    teams: List<Team>,
    members: List<Member>,
    requests: List<LeaveRequest>,
    auditLogs: List<AuditLog>,
    sqlStats: LocalSqlStats,
    selectedSubTab: HrSubTab,
    reportYear: Int,
    reportMonth: Int,
    reportTeamFilter: String?,
    verificationCases: List<VerificationTestCase>,
    isRunningTests: Boolean,
    isBusy: Boolean,
    onSelectSubTab: (HrSubTab) -> Unit,
    onShiftMonth: (Int) -> Unit,
    onSelectTeamFilter: (String?) -> Unit,
    onExportCsv: () -> Unit,
    onCreateTeam: (String, String) -> Unit,
    onUpdateMemberRoleAndTeams: (String, UserRole, Team, List<String>) -> Unit,
    onAdjustBalance: (String, LeaveType, Int, String) -> Unit,
    onCopyInviteCode: (String) -> Unit,
    onRunVerificationSuite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Sub-navigation segmented chip row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HrSubTab.entries) { subTab ->
                FilterChip(
                    selected = selectedSubTab == subTab,
                    onClick = { onSelectSubTab(subTab) },
                    label = { Text(subTab.label) },
                    modifier = Modifier.testTag("hr_subtab_${subTab.name.lowercase()}")
                )
            }
        }

        when (selectedSubTab) {
            HrSubTab.REPORT -> HrMonthlyReportSection(
                teams = teams,
                requests = requests,
                auditLogs = auditLogs,
                sqlStats = sqlStats,
                year = reportYear,
                month = reportMonth,
                teamFilter = reportTeamFilter,
                onShiftMonth = onShiftMonth,
                onSelectTeamFilter = onSelectTeamFilter,
                onExportCsv = onExportCsv
            )
            HrSubTab.AUDIT -> HrAuditTrailSection(auditLogs = auditLogs)
            HrSubTab.ORG -> HrOrgAdminSection(
                workspace = workspace,
                currentMember = currentMember,
                teams = teams,
                members = members,
                isBusy = isBusy,
                onCreateTeam = onCreateTeam,
                onUpdateMemberRoleAndTeams = onUpdateMemberRoleAndTeams,
                onAdjustBalance = onAdjustBalance,
                onCopyInviteCode = onCopyInviteCode
            )
            HrSubTab.TESTS -> HrVerificationSuiteSection(
                currentMember = currentMember,
                cases = verificationCases,
                isRunning = isRunningTests,
                onRunAllTests = onRunVerificationSuite
            )
        }
    }
}

@Composable
private fun HrMonthlyReportSection(
    teams: List<Team>,
    requests: List<LeaveRequest>,
    auditLogs: List<AuditLog>,
    sqlStats: LocalSqlStats,
    year: Int,
    month: Int,
    teamFilter: String?,
    onShiftMonth: (Int) -> Unit,
    onSelectTeamFilter: (String?) -> Unit,
    onExportCsv: () -> Unit
) {
    val approvedAbsences = remember(requests, year, month, teamFilter) {
        requests.filter { req ->
            !req.syntheticTestRecord &&
                req.parsedStatus == RequestStatus.APPROVED &&
                LeaveValidationEngine.requestMatchesMonth(req, year, month) &&
                (teamFilter == null || req.teamId == teamFilter)
        }.sortedBy { it.startDate }
    }
    val pilotMetrics = remember(requests, auditLogs, year, month, teamFilter) {
        LeaveValidationEngine.computePilotMetrics(requests, auditLogs, year, month, teamFilter)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SqlSyncStatusCard(sqlStats = sqlStats)
        }
        // Month Switcher + CSV Export
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { onShiftMonth(-1) },
                            modifier = Modifier.testTag("report_prev_month_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = stringResource(R.string.cd_previous_month)
                            )
                        }
                        Text(
                            text = LeaveValidationEngine.formatMonthLabel(year, month),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { onShiftMonth(1) },
                            modifier = Modifier.testTag("report_next_month_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.cd_next_month)
                            )
                        }
                    }

                    Button(
                        onClick = onExportCsv,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_export_csv))
                    }
                }
            }
        }

        // Team filter strip
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = teamFilter == null,
                        onClick = { onSelectTeamFilter(null) },
                        label = { Text("All Teams") }
                    )
                }
                items(teams, key = { it.id }) { team ->
                    FilterChip(
                        selected = teamFilter == team.id,
                        onClick = { onSelectTeamFilter(team.id) },
                        label = { Text(team.name) }
                    )
                }
            }
        }

        // Pilot Impact KPIs (Average Approval Time, Approved Days, Balance Corrections)
        item {
            Text(
                text = "Pilot Impact KPIs",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                KpiMetricTile(
                    label = "Avg Approval",
                    value = LeaveValidationEngine.formatApprovalDuration(pilotMetrics.avgApprovalMinutes),
                    subtitle = "${pilotMetrics.approvedRequestsCount} approved",
                    icon = Icons.Rounded.Speed,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricTile(
                    label = "Approved Days",
                    value = "${pilotMetrics.totalApprovedDays}d",
                    subtitle = "Planned cover",
                    icon = Icons.Rounded.CalendarMonth,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricTile(
                    label = "Corrections",
                    value = pilotMetrics.monthlyBalanceCorrections.toString(),
                    subtitle = "Manual HR edits",
                    icon = Icons.Rounded.EditNote,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text(
                text = "Approved Absences (${approvedAbsences.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (approvedAbsences.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Assessment,
                    title = "No approved absences this month",
                    subtitle = "Approved leave requests for ${LeaveValidationEngine.formatMonthLabel(year, month)} appear here automatically."
                )
            }
        } else {
            items(approvedAbsences, key = { it.id }) { req ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = req.employeeName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${req.parsedType.label} · ${req.dayCount}d",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "${req.teamName} · ${LeaveValidationEngine.formatDisplayDate(req.startDate)} → ${LeaveValidationEngine.formatDisplayDate(req.endDate)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!req.decidedByName.isNullOrBlank()) {
                            Text(
                                text = "Approved by ${req.decidedByName} · ${LeaveValidationEngine.formatTimestamp(req.decidedAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HrAuditTrailSection(auditLogs: List<AuditLog>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Immutable Decision & Balance History",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Every approval, rejection, and manual HR adjustment is permanently traced to its actor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (auditLogs.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.History,
                    title = "No audit events recorded yet",
                    subtitle = "Decisions and balance adjustments will appear here with before/after day counts."
                )
            }
        } else {
            items(auditLogs, key = { it.id }) { log ->
                val deltaText = when {
                    log.daysChanged > 0 -> "+${log.daysChanged}d"
                    log.daysChanged < 0 -> "${log.daysChanged}d"
                    else -> "0d"
                }
                val actionColor = when (log.parsedAction) {
                    AuditAction.APPROVED -> MaterialTheme.colorScheme.tertiary
                    AuditAction.DECLINED -> MaterialTheme.colorScheme.error
                    AuditAction.BALANCE_ADJUSTED -> MaterialTheme.colorScheme.primary
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
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${log.parsedAction.label} · ${log.employeeName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = actionColor
                            )
                            Text(
                                text = LeaveValidationEngine.formatTimestamp(log.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "${log.parsedType.label}: ${log.balanceBefore}d → ${log.balanceAfter}d ($deltaText)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            RoleBadge(role = log.parsedActorRole)
                        }
                        Text(
                            text = "Actor: ${log.actorName} (${log.teamName})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (log.comment.isNotBlank()) {
                            Text(
                                text = "\"${log.comment}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
