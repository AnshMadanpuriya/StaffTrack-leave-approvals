package com.example.domain

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Pure API-24-compatible ISO date & leave validation engine shared across UI, repositories, and unit tests.
 */
object LeaveValidationEngine {

    private val isoRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")

    fun isValidIsoDate(date: String): Boolean {
        if (!isoRegex.matches(date)) return false
        val parts = date.split("-")
        val year = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val day = parts[2].toIntOrNull() ?: return false
        if (year < 2020 || year > 2100 || month !in 1..12 || day !in 1..31) return false
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            isLenient = false
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return try {
            cal.timeInMillis
            cal.get(Calendar.YEAR) == year &&
                cal.get(Calendar.MONTH) == month - 1 &&
                cal.get(Calendar.DAY_OF_MONTH) == day
        } catch (_: Exception) {
            false
        }
    }

    fun parseIsoToEpochDay(date: String): Long? {
        if (!isValidIsoDate(date)) return null
        val parts = date.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis / 86_400_000L
    }

    fun epochMillisToIsoDate(utcMillis: Long): String {
        val cal = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    fun todayIsoDate(): String {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
    }

    fun addDaysToIsoDate(isoDate: String, deltaDays: Int): String {
        val epochDay = parseIsoToEpochDay(isoDate) ?: return isoDate
        return epochMillisToIsoDate((epochDay + deltaDays) * 86_400_000L)
    }

    fun inclusiveDayCount(startDate: String, endDate: String): Int {
        val start = parseIsoToEpochDay(startDate) ?: return -1
        val end = parseIsoToEpochDay(endDate) ?: return -1
        if (end < start) return -1
        return (end - start + 1).toInt()
    }

    fun rangesOverlap(startA: String, endA: String, startB: String, endB: String): Boolean {
        val a1 = parseIsoToEpochDay(startA) ?: return false
        val a2 = parseIsoToEpochDay(endA) ?: return false
        val b1 = parseIsoToEpochDay(startB) ?: return false
        val b2 = parseIsoToEpochDay(endB) ?: return false
        return a1 <= b2 && b1 <= a2
    }

    fun findOverlappingRequest(
        employeeUid: String,
        startDate: String,
        endDate: String,
        existingRequests: List<LeaveRequest>
    ): LeaveRequest? {
        return existingRequests.firstOrNull { req ->
            req.employeeUid == employeeUid &&
                !req.syntheticTestRecord &&
                (req.parsedStatus == RequestStatus.PENDING || req.parsedStatus == RequestStatus.APPROVED) &&
                rangesOverlap(startDate, endDate, req.startDate, req.endDate)
        }
    }

    fun validateSubmission(
        member: Member,
        leaveType: LeaveType,
        startDate: String,
        endDate: String,
        existingRequests: List<LeaveRequest>
    ): String? {
        if (!isValidIsoDate(startDate) || !isValidIsoDate(endDate)) {
            return "Please select valid start and end dates."
        }
        val days = inclusiveDayCount(startDate, endDate)
        if (days <= 0) {
            return "End date cannot be earlier than start date."
        }
        if (days > 90) {
            return "Single leave requests cannot exceed 90 days."
        }
        val overlap = findOverlappingRequest(member.uid, startDate, endDate, existingRequests)
        if (overlap != null) {
            return "Dates overlap with your ${overlap.parsedStatus.label.lowercase()} ${overlap.parsedType.label} request (${overlap.startDate} to ${overlap.endDate})."
        }
        if (leaveType.isPaidTracked) {
            val available = member.availableDaysFor(leaveType)
            if (days > available) {
                return "Insufficient ${leaveType.label} balance: $days day(s) requested, only $available remaining."
            }
        }
        return null
    }

    fun canDecideForTeam(actor: Member, targetTeamId: String): Boolean {
        return when (actor.parsedRole) {
            UserRole.HR -> true
            UserRole.MANAGER -> targetTeamId in actor.managedTeamIds
            UserRole.EMPLOYEE -> false
        }
    }

    fun formatDisplayDate(isoDate: String): String {
        val epochDay = parseIsoToEpochDay(isoDate) ?: return isoDate
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(epochDay * 86_400_000L))
    }

    fun formatTimestamp(timestamp: Timestamp?): String {
        if (timestamp == null) return "Just now"
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.US)
        return sdf.format(timestamp.toDate())
    }

    fun formatMonthLabel(year: Int, month: Int): String {
        val cal = GregorianCalendar(year, month - 1, 1)
        return SimpleDateFormat("MMMM yyyy", Locale.US).format(cal.time)
    }

    fun monthPrefix(year: Int, month: Int): String =
        String.format(Locale.US, "%04d-%02d", year, month)

    fun requestMatchesMonth(request: LeaveRequest, year: Int, month: Int): Boolean {
        val prefix = monthPrefix(year, month)
        return request.startDate.startsWith(prefix) || request.endDate.startsWith(prefix)
    }

    fun computePilotMetrics(
        requests: List<LeaveRequest>,
        auditLogs: List<AuditLog>,
        year: Int,
        month: Int,
        teamIdFilter: String?
    ): PilotMetrics {
        val approved = requests.filter { req ->
            !req.syntheticTestRecord &&
                req.parsedStatus == RequestStatus.APPROVED &&
                requestMatchesMonth(req, year, month) &&
                (teamIdFilter == null || req.teamId == teamIdFilter)
        }
        val durationsMinutes = approved.mapNotNull { req ->
            val start = req.submittedAt?.toDate()?.time
            val end = req.decidedAt?.toDate()?.time
            if (start != null && end != null && end >= start) {
                (end - start) / 60_000.0
            } else null
        }
        val avgMinutes = if (durationsMinutes.isNotEmpty()) durationsMinutes.average() else null

        val cal = Calendar.getInstance()
        val corrections = auditLogs.count { log ->
            if (log.syntheticTestRecord || log.parsedAction != AuditAction.BALANCE_ADJUSTED) {
                false
            } else {
                val ts = log.createdAt?.toDate()
                if (ts != null) {
                    cal.time = ts
                    cal.get(Calendar.YEAR) == year && (cal.get(Calendar.MONTH) + 1) == month
                } else true
            }
        }

        return PilotMetrics(
            approvedRequestsCount = approved.size,
            totalApprovedDays = approved.sumOf { it.dayCount },
            avgApprovalMinutes = avgMinutes,
            monthlyBalanceCorrections = corrections
        )
    }

    fun formatApprovalDuration(minutes: Double?): String {
        if (minutes == null) return "—"
        return when {
            minutes < 1.0 -> "< 1m"
            minutes < 60.0 -> "${minutes.toInt()}m"
            else -> {
                val hrs = minutes / 60.0
                String.format(Locale.US, "%.1fh", hrs)
            }
        }
    }

    fun buildMonthlyCsv(
        workspaceName: String,
        year: Int,
        month: Int,
        approvedRequests: List<LeaveRequest>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Workspace,Report Month,Employee,Team,Leave Type,Start Date,End Date,Days,Approved By,Decision Comment,AI Assisted")
        val monthStr = monthPrefix(year, month)
        for (req in approvedRequests) {
            val row = listOf(
                workspaceName,
                monthStr,
                req.employeeName,
                req.teamName,
                req.parsedType.label,
                req.startDate,
                req.endDate,
                req.dayCount.toString(),
                req.decidedByName.orEmpty(),
                req.decisionComment.orEmpty(),
                if (req.aiAssisted) "Yes" else "No"
            ).joinToString(",") { csvEscape(it) }
            sb.appendLine(row)
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    fun generateInviteCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = java.security.SecureRandom()
        return buildString(6) {
            repeat(6) {
                append(alphabet[abs(random.nextInt()) % alphabet.length])
            }
        }
    }
}
