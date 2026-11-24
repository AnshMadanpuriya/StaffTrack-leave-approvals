package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.LocalSqlStats
import com.example.data.repository.AiLeaveParserRepository
import com.example.data.repository.ChatPersona
import com.example.data.repository.GeminiChatRepository
import com.example.data.repository.LocalSqlCacheRepository
import com.example.data.repository.StaffTrackRepository
import com.example.data.repository.VerificationSuiteRepository
import com.example.domain.AiParseState
import com.example.domain.ApprovalOutcome
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
import com.example.domain.VerificationStatus
import com.example.domain.VerificationTestCase
import com.example.domain.Workspace
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

private const val VM_TAG = "StaffTrackVM"

sealed interface WorkspaceLoadState {
    data object Loading : WorkspaceLoadState
    data object NeedsOnboarding : WorkspaceLoadState
    data class Ready(
        val workspace: Workspace,
        val currentMember: Member?,
        val teams: List<Team>,
        val members: List<Member>,
        val leaveRequests: List<LeaveRequest>,
        val auditLogs: List<AuditLog>,
        val notifications: List<NotificationItem>
    ) : WorkspaceLoadState
    data class Error(val message: String) : WorkspaceLoadState
}

enum class MainTab { MY_LEAVE, APPROVALS, AI_COPILOT, HR_HUB }

enum class HrSubTab(val label: String) {
    REPORT("Report"),
    AUDIT("Audit Log"),
    ORG("Org & Roles"),
    TESTS("Integrity Tests")
}

@OptIn(ExperimentalCoroutinesApi::class)
class StaffTrackViewModel(
    private val repository: StaffTrackRepository,
    private val aiParser: AiLeaveParserRepository,
    private val verificationSuite: VerificationSuiteRepository,
    private val localSqlRepo: LocalSqlCacheRepository,
    private val chatRepository: GeminiChatRepository,
    val currentUserId: String,
    val currentUserDisplayName: String
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(MainTab.MY_LEAVE)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _selectedHrSubTab = MutableStateFlow(HrSubTab.REPORT)
    val selectedHrSubTab: StateFlow<HrSubTab> = _selectedHrSubTab.asStateFlow()

    private val _actionLoading = MutableStateFlow(false)
    val actionLoading: StateFlow<Boolean> = _actionLoading.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _aiParseState = MutableStateFlow<AiParseState>(AiParseState.Idle)
    val aiParseState: StateFlow<AiParseState> = _aiParseState.asStateFlow()

    private val _verificationCases = MutableStateFlow(verificationSuite.initialCases())
    val verificationCases: StateFlow<List<VerificationTestCase>> = _verificationCases.asStateFlow()

    private val _isRunningTests = MutableStateFlow(false)
    val isRunningTests: StateFlow<Boolean> = _isRunningTests.asStateFlow()

    // Multi-turn Gemini Chatbot state
    private val _selectedPersona = MutableStateFlow(ChatPersona.LEAVE_ADVISOR)
    val selectedPersona: StateFlow<ChatPersona> = _selectedPersona.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isChatStreaming = MutableStateFlow(false)
    val isChatStreaming: StateFlow<Boolean> = _isChatStreaming.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    val chatMessages: StateFlow<List<ChatMessageEntity>> = localSqlRepo
        .observeChatHistory(currentUserId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val cal = Calendar.getInstance()
    private val _reportYear = MutableStateFlow(cal.get(Calendar.YEAR))
    val reportYear: StateFlow<Int> = _reportYear.asStateFlow()

    private val _reportMonth = MutableStateFlow(cal.get(Calendar.MONTH) + 1)
    val reportMonth: StateFlow<Int> = _reportMonth.asStateFlow()

    private val _reportTeamFilter = MutableStateFlow<String?>(null)
    val reportTeamFilter: StateFlow<String?> = _reportTeamFilter.asStateFlow()

    val workspaceState: StateFlow<WorkspaceLoadState> = repository.observeUserWorkspaceIndex(currentUserId)
        .flatMapLatest { index: UserWorkspaceIndex? ->
            val wsId = index?.workspaceId
            if (wsId.isNullOrBlank()) {
                flowOf<WorkspaceLoadState>(WorkspaceLoadState.NeedsOnboarding)
            } else {
                combine(
                    repository.observeWorkspace(wsId, currentUserId),
                    repository.observeTeams(wsId, currentUserId),
                    repository.observeMembers(wsId, currentUserId),
                    repository.observeLeaveRequests(wsId, currentUserId),
                    repository.observeAuditLogs(wsId, currentUserId),
                    repository.observeNotifications(wsId, currentUserId)
                ) { values ->
                    val ws = values[0] as Workspace?
                    @Suppress("UNCHECKED_CAST")
                    val teams = values[1] as List<Team>
                    @Suppress("UNCHECKED_CAST")
                    val members = values[2] as List<Member>
                    @Suppress("UNCHECKED_CAST")
                    val requests = values[3] as List<LeaveRequest>
                    @Suppress("UNCHECKED_CAST")
                    val audits = values[4] as List<AuditLog>
                    @Suppress("UNCHECKED_CAST")
                    val notifs = values[5] as List<NotificationItem>

                    if (ws == null) {
                        WorkspaceLoadState.NeedsOnboarding
                    } else {
                        val me = members.firstOrNull { it.uid == currentUserId }
                        WorkspaceLoadState.Ready(
                            workspace = ws,
                            currentMember = me,
                            teams = teams,
                            members = members,
                            leaveRequests = requests,
                            auditLogs = audits,
                            notifications = notifs
                        )
                    }
                }.onEach { readyState ->
                    if (readyState is WorkspaceLoadState.Ready) {
                        // Mirror cloud Firestore snapshots into local Room SQLite tables asynchronously
                        viewModelScope.launch {
                            try {
                                localSqlRepo.mirrorLeavesToSql(readyState.workspace.id, readyState.leaveRequests)
                                localSqlRepo.mirrorAuditsToSql(readyState.workspace.id, readyState.auditLogs)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(VM_TAG, "Local SQL mirror sync warning: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.w(VM_TAG, "Workspace stream error", root)
            emit(WorkspaceLoadState.Error(root.message ?: "Failed to sync workspace data."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = WorkspaceLoadState.Loading
        )

    val sqlStats: StateFlow<LocalSqlStats> = workspaceState
        .flatMapLatest { state ->
            if (state is WorkspaceLoadState.Ready) {
                localSqlRepo.observeSqlStats(state.workspace.id, currentUserId)
            } else {
                flowOf(LocalSqlStats())
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = LocalSqlStats()
        )

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun selectHrSubTab(subTab: HrSubTab) {
        _selectedHrSubTab.value = subTab
    }

    fun selectChatPersona(persona: ChatPersona) {
        _selectedPersona.value = persona
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun shiftReportMonth(delta: Int) {
        val c = Calendar.getInstance().apply {
            set(_reportYear.value, _reportMonth.value - 1, 1)
            add(Calendar.MONTH, delta)
        }
        _reportYear.value = c.get(Calendar.YEAR)
        _reportMonth.value = c.get(Calendar.MONTH) + 1
    }

    fun setReportTeamFilter(teamId: String?) {
        _reportTeamFilter.value = teamId
    }

    // --- Multi-Turn Gemini Chatbot ---

    fun sendChatMessage(rawText: String) {
        val prompt = rawText.trim()
        if (prompt.isEmpty() || _isChatStreaming.value) return
        val ready = workspaceState.value as? WorkspaceLoadState.Ready
        val persona = _selectedPersona.value
        val priorHistory = chatMessages.value

        viewModelScope.launch {
            _chatError.value = null
            _isChatStreaming.value = true
            _streamingReply.value = ""

            try {
                // Persist user turn in Room SQLite immediately
                localSqlRepo.appendChatMessage(
                    userId = currentUserId,
                    role = "user",
                    persona = persona.label,
                    content = prompt
                )

                var finalReply = ""
                chatRepository.sendMessageStream(
                    persona = persona,
                    userMessage = prompt,
                    history = priorHistory,
                    workspaceName = ready?.workspace?.name ?: "StrainX Bioworks",
                    currentMember = ready?.currentMember,
                    allRequests = ready?.leaveRequests.orEmpty()
                ).collect { partial ->
                    finalReply = partial
                    _streamingReply.value = partial
                }

                if (finalReply.isNotBlank()) {
                    localSqlRepo.appendChatMessage(
                        userId = currentUserId,
                        role = "model",
                        persona = persona.label,
                        content = finalReply
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(VM_TAG, "Gemini chat error", e)
                _chatError.value = e.localizedMessage ?: "Unable to get a response from Gemini right now."
            } finally {
                _streamingReply.value = null
                _isChatStreaming.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            localSqlRepo.clearChatHistory(currentUserId)
            _chatError.value = null
            _snackbarMessage.value = "Chat history cleared from local SQLite database."
        }
    }

    // --- Onboarding ---

    fun createWorkspace(orgName: String, displayName: String) {
        viewModelScope.launch {
            _actionLoading.value = true
            val res = repository.createWorkspace(orgName, displayName)
            _actionLoading.value = false
            res.onSuccess {
                _snackbarMessage.value = "Workspace initialized. You are signed in as HR Admin."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not create workspace."
            }
        }
    }

    fun joinWorkspace(inviteCode: String, displayName: String) {
        viewModelScope.launch {
            _actionLoading.value = true
            val res = repository.joinWorkspaceByInviteCode(inviteCode, displayName)
            _actionLoading.value = false
            res.onSuccess {
                _snackbarMessage.value = "Joined workspace as Employee."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not join workspace."
            }
        }
    }

    // --- AI Natural Language Parsing ---

    fun resetAiParseState() {
        _aiParseState.value = AiParseState.Idle
    }

    fun parseLeaveWithAi(phrase: String) {
        viewModelScope.launch {
            _aiParseState.value = AiParseState.Loading
            _aiParseState.value = aiParser.parseLeavePhrase(phrase)
        }
    }

    // --- Leave Submission ---

    fun submitLeaveRequest(
        leaveType: LeaveType,
        startDate: String,
        endDate: String,
        reason: String,
        aiAssisted: Boolean,
        onSuccess: () -> Unit
    ) {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return
        val me = state.currentMember ?: return
        viewModelScope.launch {
            _actionLoading.value = true
            val res = repository.submitLeaveRequest(
                workspaceId = state.workspace.id,
                member = me,
                workspaceMemberUids = state.workspace.memberUids,
                leaveType = leaveType,
                startDate = startDate,
                endDate = endDate,
                reason = reason,
                aiAssisted = aiAssisted,
                existingRequests = state.leaveRequests
            )
            _actionLoading.value = false
            res.onSuccess {
                _aiParseState.value = AiParseState.Idle
                _snackbarMessage.value = "${leaveType.label} leave request submitted for manager review."
                onSuccess()
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to submit leave request."
            }
        }
    }

    // --- Atomic Manager Decisions ---

    fun approveRequest(requestId: String, comment: String) {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return
        viewModelScope.launch {
            _actionLoading.value = true
            val res = repository.approveLeaveRequest(
                workspaceId = state.workspace.id,
                requestId = requestId,
                comment = comment,
                workspaceMemberUids = state.workspace.memberUids
            )
            _actionLoading.value = false
            res.onSuccess { outcome ->
                _snackbarMessage.value = when (outcome) {
                    is ApprovalOutcome.Approved ->
                        "Approved. Updated balance: ${outcome.newBalance}d (audit & notice saved)."
                    is ApprovalOutcome.AlreadyProcessed ->
                        "Request was already ${outcome.currentStatus.label.lowercase()} — no duplicate deduction."
                    is ApprovalOutcome.InsufficientBalance ->
                        "Too few days remain (${outcome.availableDays}d left vs ${outcome.requestedDays}d requested). Request stays Pending."
                    is ApprovalOutcome.Unauthorized -> outcome.reason
                    is ApprovalOutcome.NotFound -> outcome.reason
                    is ApprovalOutcome.Declined -> "Declined."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Approval transaction failed."
            }
        }
    }

    fun declineRequest(requestId: String, comment: String) {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return
        viewModelScope.launch {
            _actionLoading.value = true
            val res = repository.declineLeaveRequest(
                workspaceId = state.workspace.id,
                requestId = requestId,
                comment = comment,
                workspaceMemberUids = state.workspace.memberUids
            )
            _actionLoading.value = false
            res.onSuccess { outcome ->
                _snackbarMessage.value = when (outcome) {
                    is ApprovalOutcome.Declined -> "Request declined and employee notified."
                    is ApprovalOutcome.AlreadyProcessed -> "Request was already ${outcome.currentStatus.label.lowercase()}."
                    is ApprovalOutcome.Unauthorized -> outcome.reason
                    is ApprovalOutcome.NotFound -> outcome.reason
                    else -> "Decision saved."
                }
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Decline transaction failed."
            }
        }
    }

    // --- HR Admin ---

    fun createTeam(name: String, code: String) {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return
        viewModelScope.launch {
            _actionLoading.value = true
            val res = repository.createTeam(state.workspace.id, name, code, state.workspace.memberUids)
            _actionLoading.value = false
            res.onSuccess {
                _snackbarMessage.value = "Team \"$name\" created."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not create team."
            }
        }
    }

    fun updateMemberRoleAndTeams(
        targetMemberUid: String,
        role: UserRole,
        assignedTeam: Team,
        managedTeamIds: List<String>
    ) {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return
        viewModelScope.launch {
            _actionLoading.value = true
            val res = repository.updateMemberRoleAndTeams(
                workspaceId = state.workspace.id,
                targetMemberUid = targetMemberUid,
                newRole = role,
                assignedTeam = assignedTeam,
                managedTeamIds = managedTeamIds
            )
            _actionLoading.value = false
            res.onSuccess {
                _snackbarMessage.value = "Updated role and team permissions."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not update member."
            }
        }
    }

    fun adjustMemberBalance(
        targetMemberUid: String,
        leaveType: LeaveType,
        newBalance: Int,
        reason: String
    ) {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return
        val actor = state.currentMember ?: return
        viewModelScope.launch {
            _actionLoading.value = true
            val res = repository.adjustMemberBalance(
                workspaceId = state.workspace.id,
                actor = actor,
                targetMemberUid = targetMemberUid,
                leaveType = leaveType,
                newBalance = newBalance,
                reason = reason,
                workspaceMemberUids = state.workspace.memberUids
            )
            _actionLoading.value = false
            res.onSuccess {
                _snackbarMessage.value = "${leaveType.label} balance updated to ${newBalance}d (logged in audit trail)."
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Balance adjustment failed."
            }
        }
    }

    // --- Notifications ---

    fun markNotificationRead(notificationId: String) {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return
        viewModelScope.launch {
            repository.markNotificationRead(state.workspace.id, notificationId)
        }
    }

    fun markAllNotificationsRead() {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return
        val unreadIds = state.notifications.filter { !it.read }.map { it.id }
        if (unreadIds.isEmpty()) return
        viewModelScope.launch {
            repository.markAllNotificationsRead(state.workspace.id, unreadIds)
        }
    }

    // --- Live Verification Test Suite ---

    fun runVerificationSuite() {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return
        if (_isRunningTests.value) return
        viewModelScope.launch {
            _isRunningTests.value = true
            _verificationCases.value = verificationSuite.initialCases()
            val finalResults = verificationSuite.runAllVerificationTests(
                workspaceId = state.workspace.id,
                workspaceMemberUids = state.workspace.memberUids
            ) { progress ->
                _verificationCases.value = progress
            }
            _verificationCases.value = finalResults
            _isRunningTests.value = false
            val passCount = finalResults.count { it.status == VerificationStatus.PASSED }
            _snackbarMessage.value = "Verification suite finished: $passCount / ${finalResults.size} passed."
        }
    }

    fun buildCsvForCurrentFilter(): String? {
        val state = workspaceState.value as? WorkspaceLoadState.Ready ?: return null
        val y = _reportYear.value
        val m = _reportMonth.value
        val teamFilter = _reportTeamFilter.value
        val approved = state.leaveRequests.filter { req ->
            !req.syntheticTestRecord &&
                req.parsedStatus == RequestStatus.APPROVED &&
                LeaveValidationEngine.requestMatchesMonth(req, y, m) &&
                (teamFilter == null || req.teamId == teamFilter)
        }
        return LeaveValidationEngine.buildMonthlyCsv(state.workspace.name, y, m, approved)
    }
}
