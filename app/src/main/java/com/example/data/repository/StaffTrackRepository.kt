package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.domain.ApprovalOutcome
import com.example.domain.AuditAction
import com.example.domain.AuditLog
import com.example.domain.LeaveRequest
import com.example.domain.LeaveType
import com.example.domain.LeaveValidationEngine
import com.example.domain.Member
import com.example.domain.NotificationItem
import com.example.domain.RequestStatus
import com.example.domain.Team
import com.example.domain.UserRole
import com.example.domain.UserWorkspaceIndex
import com.example.domain.Workspace
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StaffTrackRepository(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    // --- Real-time Observation Streams ---

    fun observeUserWorkspaceIndex(uid: String): Flow<UserWorkspaceIndex?> {
        val path = "userWorkspaces/$uid"
        return db.collection("userWorkspaces").document(uid)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserWorkspaceIndex::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error -> logAndRethrowStreamError(error, OperationType.GET, path, uid) }
    }

    fun observeWorkspace(workspaceId: String, uid: String): Flow<Workspace?> {
        val path = "workspaces/$workspaceId"
        return db.collection("workspaces").document(workspaceId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(Workspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else null
            }
            .catch { error -> logAndRethrowStreamError(error, OperationType.GET, path, uid) }
    }

    fun observeTeams(workspaceId: String, uid: String): Flow<List<Team>> {
        val path = "workspaces/$workspaceId/teams"
        return db.collection("workspaces").document(workspaceId).collection("teams")
            .whereArrayContains("memberUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Team::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.name.lowercase() }
            }
            .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, uid) }
    }

    fun observeMembers(workspaceId: String, uid: String): Flow<List<Member>> {
        val path = "workspaces/$workspaceId/members"
        return db.collection("workspaces").document(workspaceId).collection("members")
            .whereArrayContains("viewerUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(Member::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { !it.syntheticTestRecord }
                    .sortedBy { it.displayName.lowercase() }
            }
            .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, uid) }
    }

    fun observeLeaveRequests(workspaceId: String, uid: String): Flow<List<LeaveRequest>> {
        val path = "workspaces/$workspaceId/leaveRequests"
        return db.collection("workspaces").document(workspaceId).collection("leaveRequests")
            .whereArrayContains("viewerUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(LeaveRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { !it.syntheticTestRecord }
                    .sortedByDescending { it.submittedAt?.seconds ?: 0L }
            }
            .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, uid) }
    }

    fun observeAuditLogs(workspaceId: String, uid: String): Flow<List<AuditLog>> {
        val path = "workspaces/$workspaceId/auditLogs"
        return db.collection("workspaces").document(workspaceId).collection("auditLogs")
            .whereArrayContains("viewerUids", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { !it.syntheticTestRecord }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, uid) }
    }

    fun observeNotifications(workspaceId: String, uid: String): Flow<List<NotificationItem>> {
        val path = "workspaces/$workspaceId/notifications"
        return db.collection("workspaces").document(workspaceId).collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .snapshots()
            .map { snap ->
                snap.toObjects(NotificationItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { !it.syntheticTestRecord }
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error -> logAndRethrowStreamError(error, OperationType.LIST, path, uid) }
    }

    private fun logAndRethrowStreamError(
        error: Throwable,
        op: OperationType,
        path: String,
        uid: String
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        throw error
    }

    // --- Workspace Bootstrap & Joining ---

    suspend fun createWorkspace(
        orgName: String,
        founderDisplayName: String
    ): Result<String> {
        var currentPath = "workspaces"
        return try {
            val uid = requireUserId()
            val cleanOrg = orgName.trim().take(100)
            require(cleanOrg.length >= 2) { "Organization name must be at least 2 characters." }
            val cleanName = founderDisplayName.trim().ifBlank { "HR Administrator" }.take(100)

            val wsId = "ws_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val inviteCode = LeaveValidationEngine.generateInviteCode()
            val teamBioId = "team_bioprocess"
            val teamGenId = "team_genomics"
            val teamClinId = "team_clinical"

            currentPath = "workspaces/$wsId"
            val wsRef = db.collection("workspaces").document(wsId)
            wsRef.set(
                mapOf(
                    "name" to cleanOrg,
                    "inviteCode" to inviteCode,
                    "createdByUid" to uid,
                    "memberUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            currentPath = "inviteCodes/$inviteCode"
            db.collection("inviteCodes").document(inviteCode).set(
                mapOf(
                    "workspaceId" to wsId,
                    "workspaceName" to cleanOrg,
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Create default StrainX Bioworks teams
            val defaultTeams = listOf(
                Triple(teamBioId, "Bioprocess Lab", "BIO"),
                Triple(teamGenId, "Genomics R&D", "GEN"),
                Triple(teamClinId, "Clinical Ops", "OPS")
            )
            for ((tid, tName, tCode) in defaultTeams) {
                currentPath = "workspaces/$wsId/teams/$tid"
                wsRef.collection("teams").document(tid).set(
                    mapOf(
                        "workspaceId" to wsId,
                        "name" to tName,
                        "code" to tCode,
                        "memberUids" to listOf(uid),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Create founding HR member profile
            currentPath = "workspaces/$wsId/members/$uid"
            wsRef.collection("members").document(uid).set(
                mapOf(
                    "workspaceId" to wsId,
                    "uid" to uid,
                    "displayName" to cleanName,
                    "role" to UserRole.HR.name,
                    "teamId" to teamBioId,
                    "teamName" to "Bioprocess Lab",
                    "managedTeamIds" to listOf(teamBioId, teamGenId, teamClinId),
                    "annualDays" to 15,
                    "sickDays" to 10,
                    "personalDays" to 5,
                    "unpaidUsedDays" to 0,
                    "annualAllowance" to 15,
                    "sickAllowance" to 10,
                    "personalAllowance" to 5,
                    "syntheticTestRecord" to false,
                    "viewerUids" to listOf(uid),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            currentPath = "userWorkspaces/$uid"
            db.collection("userWorkspaces").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "workspaceId" to wsId,
                    "workspaceName" to cleanOrg,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(wsId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, currentPath)
            Result.failure(e)
        }
    }

    suspend fun joinWorkspaceByInviteCode(
        rawCode: String,
        displayName: String
    ): Result<String> {
        var currentPath = "inviteCodes"
        return try {
            val uid = requireUserId()
            val code = rawCode.trim().uppercase()
            require(code.length in 4..12 && code.matches(Regex("^[A-Z0-9]+$"))) {
                "Enter a valid workspace invite code."
            }
            val cleanName = displayName.trim().ifBlank { "Team Member" }.take(100)

            currentPath = "inviteCodes/$code"
            val codeSnap = db.collection("inviteCodes").document(code).get().await()
            if (!codeSnap.exists()) {
                return Result.failure(IllegalArgumentException("No workspace found for invite code $code."))
            }
            val wsId = codeSnap.getString("workspaceId")
                ?: return Result.failure(IllegalStateException("Invalid workspace invite record."))
            val wsName = codeSnap.getString("workspaceName") ?: "StrainX Bioworks"
            val creatorUid = codeSnap.getString("createdByUid")

            val wsRef = db.collection("workspaces").document(wsId)
            val initialViewers = listOfNotNull(uid, creatorUid).distinct()

            // Step 1: Create own EMPLOYEE member doc first (grants isWorkspaceMember)
            currentPath = "workspaces/$wsId/members/$uid"
            val existingMember = wsRef.collection("members").document(uid).get().await()
            if (!existingMember.exists()) {
                wsRef.collection("members").document(uid).set(
                    mapOf(
                        "workspaceId" to wsId,
                        "uid" to uid,
                        "displayName" to cleanName,
                        "role" to UserRole.EMPLOYEE.name,
                        "teamId" to "team_bioprocess",
                        "teamName" to "Bioprocess Lab",
                        "managedTeamIds" to emptyList<String>(),
                        "annualDays" to 15,
                        "sickDays" to 10,
                        "personalDays" to 5,
                        "unpaidUsedDays" to 0,
                        "annualAllowance" to 15,
                        "sickAllowance" to 10,
                        "personalAllowance" to 5,
                        "syntheticTestRecord" to false,
                        "viewerUids" to initialViewers,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Step 2: Append self to workspace.memberUids and default teams
            currentPath = "workspaces/$wsId"
            wsRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            for (defaultTeamId in listOf("team_bioprocess", "team_genomics", "team_clinical")) {
                try {
                    wsRef.collection("teams").document(defaultTeamId).update(
                        mapOf(
                            "memberUids" to FieldValue.arrayUnion(uid),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (_: Exception) {
                    // Ignore if a default team was removed by HR
                }
            }

            // Step 3: Save user workspace pointer
            currentPath = "userWorkspaces/$uid"
            db.collection("userWorkspaces").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "workspaceId" to wsId,
                    "workspaceName" to wsName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(wsId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    // --- Employee Leave Submission ---

    suspend fun submitLeaveRequest(
        workspaceId: String,
        member: Member,
        workspaceMemberUids: List<String>,
        leaveType: LeaveType,
        startDate: String,
        endDate: String,
        reason: String,
        aiAssisted: Boolean,
        existingRequests: List<LeaveRequest>
    ): Result<String> {
        var path = "workspaces/$workspaceId/leaveRequests"
        return try {
            val uid = requireUserId()
            check(uid == member.uid) { "Authenticated user mismatch." }

            val validationError = LeaveValidationEngine.validateSubmission(
                member = member,
                leaveType = leaveType,
                startDate = startDate,
                endDate = endDate,
                existingRequests = existingRequests
            )
            if (validationError != null) {
                return Result.failure(IllegalArgumentException(validationError))
            }

            val dayCount = LeaveValidationEngine.inclusiveDayCount(startDate, endDate)
            val viewerList = (workspaceMemberUids + uid).distinct().take(50)
            val docRef = db.collection("workspaces").document(workspaceId)
                .collection("leaveRequests").document()
            path = docRef.path

            val payload = mapOf(
                "workspaceId" to workspaceId,
                "employeeUid" to member.uid,
                "employeeName" to member.displayName.take(100),
                "teamId" to member.teamId,
                "teamName" to member.teamName.take(80),
                "leaveType" to leaveType.name,
                "startDate" to startDate,
                "endDate" to endDate,
                "dayCount" to dayCount,
                "reason" to reason.trim().take(500),
                "aiAssisted" to aiAssisted,
                "status" to RequestStatus.PENDING.name,
                "syntheticTestRecord" to false,
                "viewerUids" to viewerList,
                "submittedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- Atomic Manager Approval Transaction (Locks Balance, Rechecks Status & Days, Writes Audit + Notice) ---

    suspend fun approveLeaveRequest(
        workspaceId: String,
        requestId: String,
        comment: String,
        workspaceMemberUids: List<String>,
        actingMemberDocId: String? = null
    ): Result<ApprovalOutcome> {
        val path = "workspaces/$workspaceId/leaveRequests/$requestId"
        return try {
            val authUid = requireUserId()
            val actorDocId = actingMemberDocId ?: authUid
            val wsRef = db.collection("workspaces").document(workspaceId)
            val actorRef = wsRef.collection("members").document(actorDocId)
            val requestRef = wsRef.collection("leaveRequests").document(requestId)
            val auditRef = wsRef.collection("auditLogs").document()
            val notifRef = wsRef.collection("notifications").document()
            val cleanComment = comment.trim().take(500)

            val outcome = db.runTransaction { tx ->
                // 1. Perform ALL transaction reads BEFORE any writes
                val actorSnap = tx.get(actorRef)
                val reqSnap = tx.get(requestRef)
                if (!actorSnap.exists()) {
                    return@runTransaction ApprovalOutcome.Unauthorized("Actor profile not found in workspace.")
                }
                if (!reqSnap.exists()) {
                    return@runTransaction ApprovalOutcome.NotFound("Leave request not found.")
                }

                val actor = actorSnap.toObject(Member::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@runTransaction ApprovalOutcome.Unauthorized("Invalid actor profile.")
                val req = reqSnap.toObject(LeaveRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@runTransaction ApprovalOutcome.NotFound("Invalid leave request.")

                val employeeRef = wsRef.collection("members").document(req.employeeUid)
                val employeeSnap = tx.get(employeeRef)
                if (!employeeSnap.exists()) {
                    return@runTransaction ApprovalOutcome.NotFound("Employee balance record not found.")
                }
                val employee = employeeSnap.toObject(Member::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@runTransaction ApprovalOutcome.NotFound("Invalid employee profile.")

                // 2. Verify manager's approval rights for that employee's team
                if (!LeaveValidationEngine.canDecideForTeam(actor, req.teamId)) {
                    return@runTransaction ApprovalOutcome.Unauthorized(
                        "${actor.displayName} does not have approval rights for ${req.teamName}."
                    )
                }

                // 3. Recheck PENDING status (idempotency guard against duplicate retries)
                if (req.parsedStatus != RequestStatus.PENDING) {
                    return@runTransaction ApprovalOutcome.AlreadyProcessed(req.parsedStatus)
                }

                // 4. Recheck remaining balance inside atomic transaction
                val leaveType = req.parsedType
                val balanceField: String
                val balanceBefore: Int
                val balanceAfter: Int
                val daysDelta: Int

                if (leaveType.isPaidTracked) {
                    balanceBefore = employee.availableDaysFor(leaveType)
                    if (balanceBefore < req.dayCount) {
                        // Too few days remain -> abort write cleanly so request stays PENDING
                        return@runTransaction ApprovalOutcome.InsufficientBalance(
                            availableDays = balanceBefore,
                            requestedDays = req.dayCount
                        )
                    }
                    balanceAfter = balanceBefore - req.dayCount
                    daysDelta = -req.dayCount
                    balanceField = when (leaveType) {
                        LeaveType.ANNUAL -> "annualDays"
                        LeaveType.SICK -> "sickDays"
                        LeaveType.PERSONAL -> "personalDays"
                        LeaveType.UNPAID -> "unpaidUsedDays"
                    }
                } else {
                    balanceField = "unpaidUsedDays"
                    balanceBefore = employee.unpaidUsedDays
                    balanceAfter = balanceBefore + req.dayCount
                    daysDelta = req.dayCount
                }

                val viewerList = (workspaceMemberUids + authUid + req.employeeUid).distinct().take(50)

                // 5. Commit balance deduction, approval decision, immutable audit log, and employee notification atomically
                tx.update(
                    employeeRef,
                    mapOf(
                        balanceField to balanceAfter,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.update(
                    requestRef,
                    mapOf(
                        "status" to RequestStatus.APPROVED.name,
                        "decidedAt" to FieldValue.serverTimestamp(),
                        "decidedByUid" to authUid,
                        "decidedByName" to actor.displayName.take(100),
                        "decisionComment" to cleanComment,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.set(
                    auditRef,
                    mapOf(
                        "workspaceId" to workspaceId,
                        "requestId" to requestId,
                        "employeeUid" to req.employeeUid,
                        "employeeName" to req.employeeName.take(100),
                        "teamName" to req.teamName.take(80),
                        "action" to AuditAction.APPROVED.name,
                        "leaveType" to leaveType.name,
                        "daysChanged" to daysDelta,
                        "balanceBefore" to balanceBefore,
                        "balanceAfter" to balanceAfter,
                        "actorUid" to authUid,
                        "actorName" to actor.displayName.take(100),
                        "actorRole" to actor.parsedRole.name,
                        "comment" to cleanComment,
                        "syntheticTestRecord" to req.syntheticTestRecord,
                        "viewerUids" to viewerList,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                val noticeBody = buildString {
                    append("${leaveType.label} leave (${req.startDate} to ${req.endDate}, ${req.dayCount}d) was approved by ${actor.displayName}.")
                    if (cleanComment.isNotEmpty()) append(" Comment: $cleanComment")
                }.take(500)

                tx.set(
                    notifRef,
                    mapOf(
                        "workspaceId" to workspaceId,
                        "recipientUid" to if (req.syntheticTestRecord) authUid else req.employeeUid,
                        "requestId" to requestId,
                        "title" to "${leaveType.label} Leave Approved",
                        "body" to noticeBody,
                        "outcome" to RequestStatus.APPROVED.name,
                        "actorUid" to authUid,
                        "actorName" to actor.displayName.take(100),
                        "comment" to cleanComment,
                        "read" to false,
                        "syntheticTestRecord" to req.syntheticTestRecord,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                ApprovalOutcome.Approved(newBalance = balanceAfter, auditLogId = auditRef.id)
            }.await()

            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun declineLeaveRequest(
        workspaceId: String,
        requestId: String,
        comment: String,
        workspaceMemberUids: List<String>
    ): Result<ApprovalOutcome> {
        val path = "workspaces/$workspaceId/leaveRequests/$requestId"
        return try {
            val authUid = requireUserId()
            val wsRef = db.collection("workspaces").document(workspaceId)
            val actorRef = wsRef.collection("members").document(authUid)
            val requestRef = wsRef.collection("leaveRequests").document(requestId)
            val auditRef = wsRef.collection("auditLogs").document()
            val notifRef = wsRef.collection("notifications").document()
            val cleanComment = comment.trim().take(500)

            val outcome = db.runTransaction { tx ->
                val actorSnap = tx.get(actorRef)
                val reqSnap = tx.get(requestRef)
                if (!actorSnap.exists()) return@runTransaction ApprovalOutcome.Unauthorized("Actor profile missing.")
                if (!reqSnap.exists()) return@runTransaction ApprovalOutcome.NotFound("Leave request not found.")

                val actor = actorSnap.toObject(Member::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@runTransaction ApprovalOutcome.Unauthorized("Invalid actor.")
                val req = reqSnap.toObject(LeaveRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return@runTransaction ApprovalOutcome.NotFound("Invalid request.")

                val employeeRef = wsRef.collection("members").document(req.employeeUid)
                val employeeSnap = tx.get(employeeRef)
                val employee = employeeSnap.toObject(Member::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

                if (!LeaveValidationEngine.canDecideForTeam(actor, req.teamId)) {
                    return@runTransaction ApprovalOutcome.Unauthorized(
                        "${actor.displayName} lacks approval rights for ${req.teamName}."
                    )
                }
                if (req.parsedStatus != RequestStatus.PENDING) {
                    return@runTransaction ApprovalOutcome.AlreadyProcessed(req.parsedStatus)
                }

                val currentBal = employee?.availableDaysFor(req.parsedType) ?: 0
                val viewerList = (workspaceMemberUids + authUid + req.employeeUid).distinct().take(50)

                tx.update(
                    requestRef,
                    mapOf(
                        "status" to RequestStatus.DECLINED.name,
                        "decidedAt" to FieldValue.serverTimestamp(),
                        "decidedByUid" to authUid,
                        "decidedByName" to actor.displayName.take(100),
                        "decisionComment" to cleanComment,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                tx.set(
                    auditRef,
                    mapOf(
                        "workspaceId" to workspaceId,
                        "requestId" to requestId,
                        "employeeUid" to req.employeeUid,
                        "employeeName" to req.employeeName.take(100),
                        "teamName" to req.teamName.take(80),
                        "action" to AuditAction.DECLINED.name,
                        "leaveType" to req.parsedType.name,
                        "daysChanged" to 0,
                        "balanceBefore" to currentBal,
                        "balanceAfter" to currentBal,
                        "actorUid" to authUid,
                        "actorName" to actor.displayName.take(100),
                        "actorRole" to actor.parsedRole.name,
                        "comment" to cleanComment,
                        "syntheticTestRecord" to req.syntheticTestRecord,
                        "viewerUids" to viewerList,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                val noticeBody = buildString {
                    append("${req.parsedType.label} leave (${req.startDate} to ${req.endDate}) was declined by ${actor.displayName}.")
                    if (cleanComment.isNotEmpty()) append(" Reason: $cleanComment")
                }.take(500)

                tx.set(
                    notifRef,
                    mapOf(
                        "workspaceId" to workspaceId,
                        "recipientUid" to if (req.syntheticTestRecord) authUid else req.employeeUid,
                        "requestId" to requestId,
                        "title" to "${req.parsedType.label} Leave Declined",
                        "body" to noticeBody,
                        "outcome" to RequestStatus.DECLINED.name,
                        "actorUid" to authUid,
                        "actorName" to actor.displayName.take(100),
                        "comment" to cleanComment,
                        "read" to false,
                        "syntheticTestRecord" to req.syntheticTestRecord,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                ApprovalOutcome.Declined(auditLogId = auditRef.id)
            }.await()

            Result.success(outcome)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- HR Administration & Audited Balance Corrections ---

    suspend fun createTeam(
        workspaceId: String,
        name: String,
        code: String,
        workspaceMemberUids: List<String>
    ): Result<Unit> {
        var path = "workspaces/$workspaceId/teams"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim().take(80)
            val cleanCode = code.trim().uppercase().take(16)
            require(cleanName.length >= 2) { "Team name must be at least 2 characters." }
            require(cleanCode.length >= 2) { "Team code must be at least 2 characters." }

            val docRef = db.collection("workspaces").document(workspaceId).collection("teams").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "workspaceId" to workspaceId,
                    "name" to cleanName,
                    "code" to cleanCode,
                    "memberUids" to (workspaceMemberUids + uid).distinct().take(50),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMemberRoleAndTeams(
        workspaceId: String,
        targetMemberUid: String,
        newRole: UserRole,
        assignedTeam: Team,
        managedTeamIds: List<String>
    ): Result<Unit> {
        val path = "workspaces/$workspaceId/members/$targetMemberUid"
        return try {
            requireUserId()
            val sanitizedManaged = if (newRole == UserRole.EMPLOYEE) {
                emptyList()
            } else {
                managedTeamIds.distinct().take(30)
            }
            db.collection("workspaces").document(workspaceId)
                .collection("members").document(targetMemberUid)
                .update(
                    mapOf(
                        "role" to newRole.name,
                        "teamId" to assignedTeam.id,
                        "teamName" to assignedTeam.name.take(80),
                        "managedTeamIds" to sanitizedManaged,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun adjustMemberBalance(
        workspaceId: String,
        actor: Member,
        targetMemberUid: String,
        leaveType: LeaveType,
        newBalance: Int,
        reason: String,
        workspaceMemberUids: List<String>
    ): Result<Unit> {
        val path = "workspaces/$workspaceId/members/$targetMemberUid"
        return try {
            val uid = requireUserId()
            require(newBalance in 0..365) { "Balance must be between 0 and 365 days." }
            val cleanReason = reason.trim().ifBlank { "Manual HR balance adjustment" }.take(500)
            val wsRef = db.collection("workspaces").document(workspaceId)
            val targetRef = wsRef.collection("members").document(targetMemberUid)
            val auditRef = wsRef.collection("auditLogs").document()

            db.runTransaction { tx ->
                val snap = tx.get(targetRef)
                val target = snap.toObject(Member::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: throw IllegalStateException("Member record not found.")

                val fieldName = when (leaveType) {
                    LeaveType.ANNUAL -> "annualDays"
                    LeaveType.SICK -> "sickDays"
                    LeaveType.PERSONAL -> "personalDays"
                    LeaveType.UNPAID -> "unpaidUsedDays"
                }
                val before = target.availableDaysFor(leaveType)
                val delta = newBalance - before
                val viewers = (workspaceMemberUids + uid + targetMemberUid).distinct().take(50)

                tx.update(
                    targetRef,
                    mapOf(
                        fieldName to newBalance,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    auditRef,
                    mapOf(
                        "workspaceId" to workspaceId,
                        "requestId" to "",
                        "employeeUid" to target.uid,
                        "employeeName" to target.displayName.take(100),
                        "teamName" to target.teamName.take(80),
                        "action" to AuditAction.BALANCE_ADJUSTED.name,
                        "leaveType" to leaveType.name,
                        "daysChanged" to delta,
                        "balanceBefore" to before,
                        "balanceAfter" to newBalance,
                        "actorUid" to uid,
                        "actorName" to actor.displayName.take(100),
                        "actorRole" to actor.parsedRole.name,
                        "comment" to cleanReason,
                        "syntheticTestRecord" to false,
                        "viewerUids" to viewers,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Notifications ---

    suspend fun markNotificationRead(workspaceId: String, notificationId: String): Result<Unit> {
        val path = "workspaces/$workspaceId/notifications/$notificationId"
        return try {
            requireUserId()
            db.collection("workspaces").document(workspaceId)
                .collection("notifications").document(notificationId)
                .update(
                    mapOf(
                        "read" to true,
                        "readAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(workspaceId: String, unreadIds: List<String>): Result<Unit> {
        val path = "workspaces/$workspaceId/notifications"
        return try {
            requireUserId()
            if (unreadIds.isEmpty()) return Result.success(Unit)
            val batch = db.batch()
            val col = db.collection("workspaces").document(workspaceId).collection("notifications")
            for (id in unreadIds.take(40)) {
                batch.update(
                    col.document(id),
                    mapOf(
                        "read" to true,
                        "readAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
