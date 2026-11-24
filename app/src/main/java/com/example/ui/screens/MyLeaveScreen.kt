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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.LeaveRequest
import com.example.domain.LeaveType
import com.example.domain.LeaveValidationEngine
import com.example.domain.Member
import com.example.domain.RequestStatus
import com.example.ui.components.AiBadge
import com.example.ui.components.BalanceMetricCard
import com.example.ui.components.BiotechHeroBanner
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun MyLeaveScreen(
    member: Member,
    myRequests: List<LeaveRequest>,
    modifier: Modifier = Modifier
) {
    val totalPaidLeft = member.annualDays + member.sickDays + member.personalDays
    val myPendingCount = myRequests.count { it.parsedStatus == RequestStatus.PENDING }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Executive Biotech Hero Banner
        item {
            BiotechHeroBanner(
                displayName = member.displayName,
                teamName = member.teamName,
                role = member.parsedRole,
                totalPaidRemaining = totalPaidLeft,
                pendingCount = myPendingCount
            )
        }

        // Balance 2x2 Grid
        item {
            Text(
                text = "Available Balances",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BalanceMetricCard(
                        leaveType = LeaveType.ANNUAL,
                        daysValue = member.annualDays,
                        allowance = member.annualAllowance,
                        modifier = Modifier.weight(1f)
                    )
                    BalanceMetricCard(
                        leaveType = LeaveType.SICK,
                        daysValue = member.sickDays,
                        allowance = member.sickAllowance,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BalanceMetricCard(
                        leaveType = LeaveType.PERSONAL,
                        daysValue = member.personalDays,
                        allowance = member.personalAllowance,
                        modifier = Modifier.weight(1f)
                    )
                    BalanceMetricCard(
                        leaveType = LeaveType.UNPAID,
                        daysValue = member.unpaidUsedDays,
                        allowance = 0,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // My Requests Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Text(
                    text = "My Leave Requests",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${myRequests.size} total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (myRequests.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.EventAvailable,
                    title = "No leave requests yet",
                    subtitle = "Tap Request Leave below to pick dates or type a natural phrase with Gemini AI."
                )
            }
        } else {
            items(myRequests, key = { it.id }) { req ->
                EmployeeLeaveRequestCard(request = req)
            }
        }
    }
}

@Composable
private fun EmployeeLeaveRequestCard(request: LeaveRequest) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${request.parsedType.label} Leave",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (request.aiAssisted) {
                        AiBadge()
                    }
                }
                StatusPill(status = request.parsedStatus)
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
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "${request.dayCount}d",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (request.reason.isNotBlank()) {
                Text(
                    text = request.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!request.decidedByName.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(15.dp)
                    )
                    Column {
                        Text(
                            text = "${request.parsedStatus.label} by ${request.decidedByName} · ${LeaveValidationEngine.formatTimestamp(request.decidedAt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!request.decisionComment.isNullOrBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "\"${request.decisionComment}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
