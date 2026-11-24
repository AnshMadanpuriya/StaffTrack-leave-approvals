package com.example

import com.example.domain.AuditAction
import com.example.domain.AuditLog
import com.example.domain.LeaveRequest
import com.example.domain.LeaveType
import com.example.domain.LeaveValidationEngine
import com.example.domain.Member
import com.example.domain.RequestStatus
import com.example.domain.UserRole
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class LeaveValidationEngineTest {

    @Test
    fun inclusiveDayCount_validatesOrderAndInclusiveBounds() {
        assertEquals(2, LeaveValidationEngine.inclusiveDayCount("2026-11-23", "2026-11-24"))
        assertEquals(1, LeaveValidationEngine.inclusiveDayCount("2026-11-23", "2026-11-23"))
        assertEquals(-1, LeaveValidationEngine.inclusiveDayCount("2026-11-25", "2026-11-24"))
    }

    @Test
    fun validateSubmission_blocksOverlapsInvertedDatesAndInsufficientBalance() {
        val member = Member(
            uid = "emp_1",
            annualDays = 5,
            sickDays = 3,
            personalDays = 2
        )
        val existing = listOf(
            LeaveRequest(
                id = "r1",
                employeeUid = "emp_1",
                leaveType = LeaveType.ANNUAL.name,
                startDate = "2026-11-23",
                endDate = "2026-11-24",
                dayCount = 2,
                status = RequestStatus.APPROVED.name
            )
        )

        // Overlapping dates rejected
        val overlapError = LeaveValidationEngine.validateSubmission(
            member, LeaveType.ANNUAL, "2026-11-24", "2026-11-25", existing
        )
        assertNotNull(overlapError)
        assertTrue(overlapError!!.contains("overlap", ignoreCase = true))

        // End date before start date rejected
        val invertedError = LeaveValidationEngine.validateSubmission(
            member, LeaveType.ANNUAL, "2026-11-28", "2026-11-26", existing
        )
        assertNotNull(invertedError)

        // Request exceeding remaining paid balance rejected
        val lowBalanceError = LeaveValidationEngine.validateSubmission(
            member, LeaveType.ANNUAL, "2026-12-01", "2026-12-06", existing
        )
        assertNotNull(lowBalanceError)
        assertTrue(lowBalanceError!!.contains("Insufficient", ignoreCase = true))

        // Valid non-overlapping paid request passes
        assertNull(
            LeaveValidationEngine.validateSubmission(
                member, LeaveType.ANNUAL, "2026-12-01", "2026-12-03", existing
            )
        )

        // Unpaid leave passes without finite balance check
        assertNull(
            LeaveValidationEngine.validateSubmission(
                member, LeaveType.UNPAID, "2026-12-10", "2026-12-20", existing
            )
        )
    }

    @Test
    fun canDecideForTeam_enforcesTeamScopedManagerAuthorization() {
        val hr = Member(role = UserRole.HR.name, managedTeamIds = emptyList())
        val bioMgr = Member(role = UserRole.MANAGER.name, managedTeamIds = listOf("team_bio"))
        val emp = Member(role = UserRole.EMPLOYEE.name, managedTeamIds = listOf("team_bio"))

        assertTrue(LeaveValidationEngine.canDecideForTeam(hr, "team_any"))
        assertTrue(LeaveValidationEngine.canDecideForTeam(bioMgr, "team_bio"))
        assertFalse(LeaveValidationEngine.canDecideForTeam(bioMgr, "team_genomics"))
        assertFalse(LeaveValidationEngine.canDecideForTeam(emp, "team_bio"))
    }

    @Test
    fun buildMonthlyCsv_escapesCommasAndQuotesPerRfc4180() {
        val req = LeaveRequest(
            id = "r1",
            employeeName = "Patel, Priya",
            teamName = "Bioprocess Lab",
            leaveType = LeaveType.ANNUAL.name,
            startDate = "2026-11-23",
            endDate = "2026-11-24",
            dayCount = 2,
            status = RequestStatus.APPROVED.name,
            decidedByName = "Ansh HR",
            decisionComment = "Approved, \"cover set\"",
            aiAssisted = true
        )
        val csv = LeaveValidationEngine.buildMonthlyCsv("StrainX Bioworks", 2026, 11, listOf(req))
        assertTrue(csv.contains("\"Patel, Priya\""))
        assertTrue(csv.contains("\"Approved, \"\"cover set\"\"\""))
    }

    @Test
    fun computePilotMetrics_aggregatesApprovalTurnaroundAndBalanceCorrections() {
        val submitted = Timestamp(Date(1_795_420_000_000L))
        val decided = Timestamp(Date(1_795_420_000_000L + 30 * 60_000L)) // +30 min
        val req = LeaveRequest(
            id = "r1",
            teamId = "team_bio",
            startDate = "2026-11-23",
            endDate = "2026-11-24",
            dayCount = 2,
            status = RequestStatus.APPROVED.name,
            submittedAt = submitted,
            decidedAt = decided
        )
        val correctionLog = AuditLog(
            id = "log1",
            action = AuditAction.BALANCE_ADJUSTED.name,
            createdAt = Timestamp(Date(1_795_420_000_000L))
        )
        val metrics = LeaveValidationEngine.computePilotMetrics(
            requests = listOf(req),
            auditLogs = listOf(correctionLog),
            year = 2026,
            month = 11,
            teamIdFilter = null
        )
        assertEquals(1, metrics.approvedRequestsCount)
        assertEquals(2, metrics.totalApprovedDays)
        assertNotNull(metrics.avgApprovalMinutes)
        assertEquals(30.0, metrics.avgApprovalMinutes!!, 0.1)
        assertEquals(1, metrics.monthlyBalanceCorrections)
    }
}
