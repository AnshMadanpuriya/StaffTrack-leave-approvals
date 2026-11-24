package com.example.data.repository

import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.domain.ApprovalOutcome
import com.example.domain.LeaveRequest
import com.example.domain.LeaveType
import com.example.domain.LeaveValidationEngine
import com.example.domain.Member
import com.example.domain.RequestStatus
import com.example.domain.UserRole
import com.example.domain.VerificationStatus
import com.example.domain.VerificationTestCase
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Runs the 5 verification scenarios from the StaffTrack implementation proposal directly against
 * live Firestore atomic transactions using isolated syntheticTestRecord documents, then cleans up.
 */
class VerificationSuiteRepository(
    private val staffTrackRepository: StaffTrackRepository
) {
    private val db get() = staffTrackRepository.db

    fun initialCases(): List<VerificationTestCase> = listOf(
        VerificationTestCase(
            id = "retry_idempotency",
            title = "1. Retry Idempotency (5d -> 2d + Retry)",
            scenario = "With 5 annual days left, approve a 2-day request and immediately retry it. Expect 3 days remaining and exactly 1 audit entry."
        ),
        VerificationTestCase(
            id = "concurrent_race",
            title = "2. Concurrent Approvals (5d vs Two 3d Requests)",
            scenario = "Reset to 5 days and launch two parallel 3-day approvals at once. Expect only 1 to succeed (2 days left) and the second to remain Pending."
        ),
        VerificationTestCase(
            id = "overlap_guard",
            title = "3. Overlapping Dates & Date-Order Guard",
            scenario = "Reject overlapping requests (Nov 23–24 vs Nov 24–25) and invalid end-before-start date ranges."
        ),
        VerificationTestCase(
            id = "unauthorized_manager",
            title = "4. Unauthorized Manager Team Rights Check",
            scenario = "A manager assigned only to Genomics R&D attempts to approve a Bioprocess Lab request. Expect Unauthorized denial with balance intact."
        ),
        VerificationTestCase(
            id = "notification_delivery",
            title = "5. Approval & Rejection Notice Delivery",
            scenario = "Approve one request and decline another with manager comments. Verify both notices arrive in the notification collection with exact comments."
        )
    )

    suspend fun runAllVerificationTests(
        workspaceId: String,
        workspaceMemberUids: List<String>,
        onProgress: (List<VerificationTestCase>) -> Unit
    ): List<VerificationTestCase> {
        val authUid = Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("Must be signed in to run verification tests.")
        val runTag = UUID.randomUUID().toString().replace("-", "").take(8)
        val wsRef = db.collection("workspaces").document(workspaceId)
        val viewers = (workspaceMemberUids + authUid).distinct().take(50)

        val results = initialCases().toMutableList()
        val createdMembers = mutableListOf<String>()
        val createdRequests = mutableListOf<String>()
        val createdAudits = mutableListOf<String>()
        val createdNotifications = mutableListOf<String>()

        fun updateCase(index: Int, status: VerificationStatus, detail: String) {
            results[index] = results[index].copy(status = status, detail = detail)
            onProgress(results.toList())
        }

        val synthEmpId = "synth_emp_$runTag"
        val synthUnauthorizedMgrId = "synth_mgr_$runTag"

        try {
            // Seed synthetic employee with 5 Annual days in Bioprocess Lab
            wsRef.collection("members").document(synthEmpId).set(
                mapOf(
                    "workspaceId" to workspaceId,
                    "uid" to synthEmpId,
                    "displayName" to "Test Employee ($runTag)",
                    "role" to UserRole.EMPLOYEE.name,
                    "teamId" to "team_bioprocess",
                    "teamName" to "Bioprocess Lab",
                    "managedTeamIds" to emptyList<String>(),
                    "annualDays" to 5,
                    "sickDays" to 10,
                    "personalDays" to 5,
                    "unpaidUsedDays" to 0,
                    "annualAllowance" to 15,
                    "sickAllowance" to 10,
                    "personalAllowance" to 5,
                    "syntheticTestRecord" to true,
                    "viewerUids" to viewers,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            createdMembers.add(synthEmpId)

            // --- TEST 1: Retry Idempotency ---
            updateCase(0, VerificationStatus.RUNNING, "Executing 2-day approval + immediate retry...")
            val req1Id = "synth_req1_$runTag"
            wsRef.collection("leaveRequests").document(req1Id).set(
                syntheticRequestPayload(
                    workspaceId, synthEmpId, "Test Employee ($runTag)",
                    "team_bioprocess", "Bioprocess Lab",
                    "2026-11-23", "2026-11-24", 2, viewers
                )
            ).await()
            createdRequests.add(req1Id)

            val firstAttempt = staffTrackRepository.approveLeaveRequest(
                workspaceId, req1Id, "Initial approval", viewers
            ).getOrThrow()
            val retryAttempt = staffTrackRepository.approveLeaveRequest(
                workspaceId, req1Id, "Duplicate retry", viewers
            ).getOrThrow()

            if (firstAttempt is ApprovalOutcome.Approved) createdAudits.add(firstAttempt.auditLogId)

            val empAfterT1 = wsRef.collection("members").document(synthEmpId).get().await()
                .toObject(Member::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val remainingAfterT1 = empAfterT1?.annualDays ?: -1
            val auditCountT1 = wsRef.collection("auditLogs")
                .whereArrayContains("viewerUids", authUid)
                .get().await()
                .documents.count { it.getString("requestId") == req1Id }

            if (firstAttempt is ApprovalOutcome.Approved &&
                retryAttempt is ApprovalOutcome.AlreadyProcessed &&
                remainingAfterT1 == 3 &&
                auditCountT1 == 1
            ) {
                updateCase(0, VerificationStatus.PASSED, "PASS: Balance = 3d, Audit entries = 1, Retry returned AlreadyProcessed.")
            } else {
                updateCase(0, VerificationStatus.FAILED, "FAIL: first=$firstAttempt, retry=$retryAttempt, bal=$remainingAfterT1, audits=$auditCountT1")
            }

            // --- TEST 2: Concurrent Approval Race ---
            updateCase(1, VerificationStatus.RUNNING, "Resetting balance to 5d and racing two 3-day approvals...")
            wsRef.collection("members").document(synthEmpId).update(
                mapOf("annualDays" to 5, "updatedAt" to FieldValue.serverTimestamp())
            ).await()

            val req2A = "synth_req2a_$runTag"
            val req2B = "synth_req2b_$runTag"
            wsRef.collection("leaveRequests").document(req2A).set(
                syntheticRequestPayload(
                    workspaceId, synthEmpId, "Test Employee ($runTag)",
                    "team_bioprocess", "Bioprocess Lab",
                    "2026-12-01", "2026-12-03", 3, viewers
                )
            ).await()
            wsRef.collection("leaveRequests").document(req2B).set(
                syntheticRequestPayload(
                    workspaceId, synthEmpId, "Test Employee ($runTag)",
                    "team_bioprocess", "Bioprocess Lab",
                    "2026-12-08", "2026-12-10", 3, viewers
                )
            ).await()
            createdRequests.addAll(listOf(req2A, req2B))

            val concurrentResults = coroutineScope {
                listOf(
                    async { staffTrackRepository.approveLeaveRequest(workspaceId, req2A, "Concurrent Mgr A", viewers) },
                    async { staffTrackRepository.approveLeaveRequest(workspaceId, req2B, "Concurrent Mgr B", viewers) }
                ).awaitAll()
            }
            val outcomes = concurrentResults.mapNotNull { it.getOrNull() }
            outcomes.filterIsInstance<ApprovalOutcome.Approved>().forEach { createdAudits.add(it.auditLogId) }

            val approvedCount = outcomes.count { it is ApprovalOutcome.Approved }
            val insufficientCount = outcomes.count { it is ApprovalOutcome.InsufficientBalance }
            val empAfterT2 = wsRef.collection("members").document(synthEmpId).get().await()
                .toObject(Member::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            val remainingAfterT2 = empAfterT2?.annualDays ?: -1

            if (approvedCount == 1 && insufficientCount == 1 && remainingAfterT2 == 2) {
                updateCase(1, VerificationStatus.PASSED, "PASS: 1 Approved, 1 stayed Pending (InsufficientBalance), 2d remaining.")
            } else {
                updateCase(1, VerificationStatus.FAILED, "FAIL: approved=$approvedCount, insufficient=$insufficientCount, bal=$remainingAfterT2, outcomes=$outcomes")
            }

            // --- TEST 3: Overlapping Dates & End-Before-Start ---
            updateCase(2, VerificationStatus.RUNNING, "Validating overlap & inverted date rejection...")
            val sampleMember = Member(uid = synthEmpId, annualDays = 10, syntheticTestRecord = false)
            val existingReqs = listOf(
                LeaveRequest(
                    id = "existing_1",
                    employeeUid = synthEmpId,
                    leaveType = LeaveType.ANNUAL.name,
                    startDate = "2026-11-23",
                    endDate = "2026-11-24",
                    dayCount = 2,
                    status = RequestStatus.APPROVED.name,
                    syntheticTestRecord = false
                )
            )
            val overlapErr = LeaveValidationEngine.validateSubmission(
                sampleMember, LeaveType.ANNUAL, "2026-11-24", "2026-11-25", existingReqs
            )
            val invertedErr = LeaveValidationEngine.validateSubmission(
                sampleMember, LeaveType.ANNUAL, "2026-11-28", "2026-11-25", emptyList()
            )
            if (overlapErr != null && overlapErr.contains("overlap", ignoreCase = true) &&
                invertedErr != null && invertedErr.contains("earlier", ignoreCase = true)
            ) {
                updateCase(2, VerificationStatus.PASSED, "PASS: Blocked overlapping dates and inverted date ranges before write.")
            } else {
                updateCase(2, VerificationStatus.FAILED, "FAIL: overlapErr=$overlapErr, invertedErr=$invertedErr")
            }

            // --- TEST 4: Unauthorized Manager Check ---
            updateCase(3, VerificationStatus.RUNNING, "Testing approval attempt by manager without Bioprocess rights...")
            wsRef.collection("members").document(synthUnauthorizedMgrId).set(
                mapOf(
                    "workspaceId" to workspaceId,
                    "uid" to synthUnauthorizedMgrId,
                    "displayName" to "Genomics-Only Mgr",
                    "role" to UserRole.MANAGER.name,
                    "teamId" to "team_genomics",
                    "teamName" to "Genomics R&D",
                    "managedTeamIds" to listOf("team_genomics"),
                    "annualDays" to 15,
                    "sickDays" to 10,
                    "personalDays" to 5,
                    "unpaidUsedDays" to 0,
                    "annualAllowance" to 15,
                    "sickAllowance" to 10,
                    "personalAllowance" to 5,
                    "syntheticTestRecord" to true,
                    "viewerUids" to viewers,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            createdMembers.add(synthUnauthorizedMgrId)

            val req4Id = "synth_req4_$runTag"
            wsRef.collection("leaveRequests").document(req4Id).set(
                syntheticRequestPayload(
                    workspaceId, synthEmpId, "Test Employee ($runTag)",
                    "team_bioprocess", "Bioprocess Lab",
                    "2026-12-15", "2026-12-16", 2, viewers
                )
            ).await()
            createdRequests.add(req4Id)

            val unauthOutcome = staffTrackRepository.approveLeaveRequest(
                workspaceId = workspaceId,
                requestId = req4Id,
                comment = "Should be blocked",
                workspaceMemberUids = viewers,
                actingMemberDocId = synthUnauthorizedMgrId
            ).getOrThrow()

            if (unauthOutcome is ApprovalOutcome.Unauthorized) {
                updateCase(3, VerificationStatus.PASSED, "PASS: Unauthorized manager blocked (${unauthOutcome.reason}).")
            } else {
                updateCase(3, VerificationStatus.FAILED, "FAIL: Expected Unauthorized, got $unauthOutcome")
            }

            // --- TEST 5: Approval & Rejection Notice Delivery ---
            updateCase(4, VerificationStatus.RUNNING, "Verifying approval & rejection notice delivery...")
            wsRef.collection("members").document(synthEmpId).update(
                mapOf("annualDays" to 5, "updatedAt" to FieldValue.serverTimestamp())
            ).await()

            val req5ApproveId = "synth_req5a_$runTag"
            val req5DeclineId = "synth_req5d_$runTag"
            wsRef.collection("leaveRequests").document(req5ApproveId).set(
                syntheticRequestPayload(
                    workspaceId, synthEmpId, "Test Employee ($runTag)",
                    "team_bioprocess", "Bioprocess Lab",
                    "2026-12-21", "2026-12-22", 2, viewers
                )
            ).await()
            wsRef.collection("leaveRequests").document(req5DeclineId).set(
                syntheticRequestPayload(
                    workspaceId, synthEmpId, "Test Employee ($runTag)",
                    "team_bioprocess", "Bioprocess Lab",
                    "2026-12-28", "2026-12-29", 2, viewers
                )
            ).await()
            createdRequests.addAll(listOf(req5ApproveId, req5DeclineId))

            val apprRes = staffTrackRepository.approveLeaveRequest(
                workspaceId, req5ApproveId, "Approved — shift covered", viewers
            ).getOrThrow()
            val declRes = staffTrackRepository.declineLeaveRequest(
                workspaceId, req5DeclineId, "Assay deadline overlap", viewers
            ).getOrThrow()
            if (apprRes is ApprovalOutcome.Approved) createdAudits.add(apprRes.auditLogId)
            if (declRes is ApprovalOutcome.Declined) createdAudits.add(declRes.auditLogId)

            val deliveredNotices = wsRef.collection("notifications")
                .whereEqualTo("recipientUid", authUid)
                .get().await().documents

            val approveNotice = deliveredNotices.firstOrNull {
                it.getString("requestId") == req5ApproveId && it.getString("outcome") == RequestStatus.APPROVED.name
            }
            val declineNotice = deliveredNotices.firstOrNull {
                it.getString("requestId") == req5DeclineId && it.getString("outcome") == RequestStatus.DECLINED.name
            }
            deliveredNotices
                .filter { it.getBoolean("syntheticTestRecord") == true }
                .forEach { createdNotifications.add(it.id) }

            if (approveNotice != null && declineNotice != null &&
                approveNotice.getString("comment") == "Approved — shift covered" &&
                declineNotice.getString("comment") == "Assay deadline overlap"
            ) {
                updateCase(4, VerificationStatus.PASSED, "PASS: Both Approved & Declined notices delivered with manager comments.")
            } else {
                updateCase(4, VerificationStatus.FAILED, "FAIL: Missing expected notification documents.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "workspaces/$workspaceId/verificationSuite")
            val firstRunning = results.indexOfFirst { it.status == VerificationStatus.RUNNING || it.status == VerificationStatus.IDLE }
            if (firstRunning >= 0) {
                updateCase(firstRunning, VerificationStatus.FAILED, "Error: ${e.localizedMessage ?: e.javaClass.simpleName}")
            }
        } finally {
            // Clean up all synthetic test documents so workspace reports remain pristine
            cleanupSyntheticDocs(wsRef.id, createdRequests, createdAudits, createdNotifications, createdMembers)
        }

        return results
    }

    private suspend fun cleanupSyntheticDocs(
        workspaceId: String,
        requestIds: List<String>,
        auditIds: List<String>,
        notifIds: List<String>,
        memberIds: List<String>
    ) {
        val wsRef = db.collection("workspaces").document(workspaceId)
        try {
            val batch = db.batch()
            requestIds.forEach { batch.delete(wsRef.collection("leaveRequests").document(it)) }
            auditIds.forEach { batch.delete(wsRef.collection("auditLogs").document(it)) }
            notifIds.forEach { batch.delete(wsRef.collection("notifications").document(it)) }
            memberIds.forEach { batch.delete(wsRef.collection("members").document(it)) }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "workspaces/$workspaceId/syntheticCleanup")
        }
    }

    private fun syntheticRequestPayload(
        workspaceId: String,
        empUid: String,
        empName: String,
        teamId: String,
        teamName: String,
        startDate: String,
        endDate: String,
        dayCount: Int,
        viewers: List<String>
    ): Map<String, Any> = mapOf(
        "workspaceId" to workspaceId,
        "employeeUid" to empUid,
        "employeeName" to empName,
        "teamId" to teamId,
        "teamName" to teamName,
        "leaveType" to LeaveType.ANNUAL.name,
        "startDate" to startDate,
        "endDate" to endDate,
        "dayCount" to dayCount,
        "reason" to "Automated verification suite check",
        "aiAssisted" to false,
        "status" to RequestStatus.PENDING.name,
        "syntheticTestRecord" to true,
        "viewerUids" to viewers,
        "submittedAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
