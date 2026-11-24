package com.example.domain

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val label: String) {
    EMPLOYEE("Employee"),
    MANAGER("Manager"),
    HR("HR Admin");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: EMPLOYEE
    }
}

enum class LeaveType(val label: String, val isPaidTracked: Boolean) {
    ANNUAL("Annual", true),
    SICK("Sick", true),
    PERSONAL("Personal", true),
    UNPAID("Unpaid", false);

    companion object {
        fun fromRaw(raw: String?): LeaveType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: ANNUAL
    }
}

enum class RequestStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    DECLINED("Declined");

    companion object {
        fun fromRaw(raw: String?): RequestStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class AuditAction(val label: String) {
    APPROVED("Approved"),
    DECLINED("Declined"),
    BALANCE_ADJUSTED("Balance Adjusted");

    companion object {
        fun fromRaw(raw: String?): AuditAction =
            entries.firstOrNull { it.name == raw } ?: APPROVED
    }
}

@Keep
@IgnoreExtraProperties
data class UserWorkspaceIndex(
    @DocumentId val id: String = "",
    val uid: String = "",
    val workspaceId: String = "",
    val workspaceName: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Workspace(
    @DocumentId val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val createdByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Team(
    @DocumentId val id: String = "",
    val workspaceId: String = "",
    val name: String = "",
    val code: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Member(
    @DocumentId val id: String = "",
    val workspaceId: String = "",
    val uid: String = "",
    val displayName: String = "",
    val role: String = UserRole.EMPLOYEE.name,
    val teamId: String = "",
    val teamName: String = "",
    val managedTeamIds: List<String> = emptyList(),
    val annualDays: Int = 15,
    val sickDays: Int = 10,
    val personalDays: Int = 5,
    val unpaidUsedDays: Int = 0,
    val annualAllowance: Int = 15,
    val sickAllowance: Int = 10,
    val personalAllowance: Int = 5,
    val syntheticTestRecord: Boolean = false,
    val viewerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedRole: UserRole get() = UserRole.fromRaw(role)

    fun availableDaysFor(type: LeaveType): Int = when (type) {
        LeaveType.ANNUAL -> annualDays
        LeaveType.SICK -> sickDays
        LeaveType.PERSONAL -> personalDays
        LeaveType.UNPAID -> unpaidUsedDays
    }

    fun allowanceFor(type: LeaveType): Int = when (type) {
        LeaveType.ANNUAL -> annualAllowance
        LeaveType.SICK -> sickAllowance
        LeaveType.PERSONAL -> personalAllowance
        LeaveType.UNPAID -> 0
    }
}

@Keep
@IgnoreExtraProperties
data class LeaveRequest(
    @DocumentId val id: String = "",
    val workspaceId: String = "",
    val employeeUid: String = "",
    val employeeName: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val leaveType: String = LeaveType.ANNUAL.name,
    val startDate: String = "",
    val endDate: String = "",
    val dayCount: Int = 1,
    val reason: String = "",
    val aiAssisted: Boolean = false,
    val status: String = RequestStatus.PENDING.name,
    val syntheticTestRecord: Boolean = false,
    val viewerUids: List<String> = emptyList(),
    @ServerTimestamp val submittedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val decidedAt: Timestamp? = null,
    val decidedByUid: String? = null,
    val decidedByName: String? = null,
    val decisionComment: String? = null
) {
    val parsedType: LeaveType get() = LeaveType.fromRaw(leaveType)
    val parsedStatus: RequestStatus get() = RequestStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val workspaceId: String = "",
    val requestId: String = "",
    val employeeUid: String = "",
    val employeeName: String = "",
    val teamName: String = "",
    val action: String = AuditAction.APPROVED.name,
    val leaveType: String = LeaveType.ANNUAL.name,
    val daysChanged: Int = 0,
    val balanceBefore: Int = 0,
    val balanceAfter: Int = 0,
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = UserRole.MANAGER.name,
    val comment: String = "",
    val syntheticTestRecord: Boolean = false,
    val viewerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedAction: AuditAction get() = AuditAction.fromRaw(action)
    val parsedType: LeaveType get() = LeaveType.fromRaw(leaveType)
    val parsedActorRole: UserRole get() = UserRole.fromRaw(actorRole)
}

@Keep
@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val workspaceId: String = "",
    val recipientUid: String = "",
    val requestId: String = "",
    val title: String = "",
    val body: String = "",
    val outcome: String = RequestStatus.APPROVED.name,
    val actorUid: String = "",
    val actorName: String = "",
    val comment: String = "",
    val read: Boolean = false,
    val syntheticTestRecord: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    val readAt: Timestamp? = null
) {
    val parsedOutcome: RequestStatus get() = RequestStatus.fromRaw(outcome)
}

sealed interface ApprovalOutcome {
    data class Approved(val newBalance: Int, val auditLogId: String) : ApprovalOutcome
    data class Declined(val auditLogId: String) : ApprovalOutcome
    data class AlreadyProcessed(val currentStatus: RequestStatus) : ApprovalOutcome
    data class InsufficientBalance(val availableDays: Int, val requestedDays: Int) : ApprovalOutcome
    data class Unauthorized(val reason: String) : ApprovalOutcome
    data class NotFound(val reason: String) : ApprovalOutcome
}

sealed interface AiParseState {
    data object Idle : AiParseState
    data object Loading : AiParseState
    data class Suggested(
        val leaveType: LeaveType,
        val startDate: String,
        val endDate: String,
        val summaryNote: String
    ) : AiParseState
    data class NeedsClarification(
        val promptMessage: String,
        val partialLeaveType: LeaveType?,
        val partialStartDate: String?,
        val partialEndDate: String?
    ) : AiParseState
    data class Error(val message: String) : AiParseState
}

data class PilotMetrics(
    val approvedRequestsCount: Int,
    val totalApprovedDays: Int,
    val avgApprovalMinutes: Double?,
    val monthlyBalanceCorrections: Int
)

enum class VerificationStatus { IDLE, RUNNING, PASSED, FAILED }

data class VerificationTestCase(
    val id: String,
    val title: String,
    val scenario: String,
    val status: VerificationStatus = VerificationStatus.IDLE,
    val detail: String = "Ready to run"
)
