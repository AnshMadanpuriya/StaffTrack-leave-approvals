package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalSqlStats
import com.example.domain.LeaveType
import com.example.domain.RequestStatus
import com.example.domain.UserRole
import com.example.ui.theme.AiAccentContainer
import com.example.ui.theme.AiAccentContainerDark
import com.example.ui.theme.AiAccentForeground
import com.example.ui.theme.AiAccentForegroundDark
import com.example.ui.theme.BiotechTeal
import com.example.ui.theme.BiotechTealDark
import com.example.ui.theme.BiotechTealLight
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.StatusApprovedBg
import com.example.ui.theme.StatusApprovedBgDark
import com.example.ui.theme.StatusApprovedFg
import com.example.ui.theme.StatusApprovedFgDark
import com.example.ui.theme.StatusDeclinedBg
import com.example.ui.theme.StatusDeclinedBgDark
import com.example.ui.theme.StatusDeclinedFg
import com.example.ui.theme.StatusDeclinedFgDark
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingBgDark
import com.example.ui.theme.StatusPendingFg
import com.example.ui.theme.StatusPendingFgDark

@Composable
fun BiotechHeroBanner(
    displayName: String,
    teamName: String,
    role: UserRole,
    totalPaidRemaining: Int,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(BiotechTealDark, BiotechTeal, Color(0xFF0A4F5C))
                )
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = BiotechTealLight.copy(alpha = 0.16f),
                radius = size.height * 0.85f,
                center = Offset(size.width * 0.92f, size.height * 0.15f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.height * 0.65f,
                center = Offset(size.width * 0.10f, size.height * 0.95f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = teamName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = role.label,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "TOTAL PAID LEFT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${totalPaidRemaining} days",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = Color.White
                        )
                    }
                }
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "PENDING REVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f)
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$pendingCount request${if (pendingCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SqlSyncStatusCard(
    sqlStats: LocalSqlStats,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = "SQLite local database synced",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dual Storage · Firestore Cloud + Room SQLite",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "SQL tables synced · ${sqlStats.leaveRows} leaves · ${sqlStats.auditRows} audits · ${sqlStats.chatRows} chat turns",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    status: RequestStatus,
    modifier: Modifier = Modifier
) {
    val dark = isSystemInDarkTheme()
    val (bg, fg, icon) = when (status) {
        RequestStatus.APPROVED -> Triple(
            if (dark) StatusApprovedBgDark else StatusApprovedBg,
            if (dark) StatusApprovedFgDark else StatusApprovedFg,
            Icons.Rounded.CheckCircle
        )
        RequestStatus.PENDING -> Triple(
            if (dark) StatusPendingBgDark else StatusPendingBg,
            if (dark) StatusPendingFgDark else StatusPendingFg,
            Icons.Rounded.Schedule
        )
        RequestStatus.DECLINED -> Triple(
            if (dark) StatusDeclinedBgDark else StatusDeclinedBg,
            if (dark) StatusDeclinedFgDark else StatusDeclinedFg,
            Icons.Rounded.Cancel
        )
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = status.label,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = fg
            )
        }
    }
}

@Composable
fun RoleBadge(
    role: UserRole,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (role) {
        UserRole.HR -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        UserRole.MANAGER -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        UserRole.EMPLOYEE -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = role.label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun AiBadge(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val bg = if (dark) AiAccentContainerDark else AiAccentContainer
    val fg = if (dark) AiAccentForegroundDark else AiAccentForeground
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "AI assisted",
                tint = fg,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "AI Assisted",
                style = MaterialTheme.typography.labelMedium,
                color = fg
            )
        }
    }
}

@Composable
fun BalanceMetricCard(
    leaveType: LeaveType,
    daysValue: Int,
    allowance: Int,
    modifier: Modifier = Modifier
) {
    val accent = when (leaveType) {
        LeaveType.ANNUAL -> MaterialTheme.colorScheme.primary
        LeaveType.SICK -> MaterialTheme.colorScheme.tertiary
        LeaveType.PERSONAL -> MaterialTheme.colorScheme.secondary
        LeaveType.UNPAID -> MaterialTheme.colorScheme.outline
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val targetProgress = if (leaveType.isPaidTracked && allowance > 0) {
        (daysValue.toFloat() / allowance.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val animatedSweep by animateFloatAsState(
        targetValue = targetProgress * 360f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 240f),
        label = "balance_arc"
    )

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
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
                    text = leaveType.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (leaveType.isPaidTracked) "$daysValue / ${allowance}d" else "${daysValue}d",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFamily),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (leaveType.isPaidTracked) "remaining" else "unpaid used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Animated circular radial gauge
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(44.dp)
            ) {
                Canvas(modifier = Modifier.size(44.dp)) {
                    val strokeWidth = 5.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = daysValue.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = accent
                )
            }
        }
    }
}

@Composable
fun KpiMetricTile(
    label: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
