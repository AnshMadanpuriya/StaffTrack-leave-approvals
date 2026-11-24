package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.authStateFlow
import com.example.data.firebase.configureAppCheck
import com.example.data.firebase.signOutFromGoogle
import com.example.data.local.StaffTrackDatabase
import com.example.data.repository.AiLeaveParserRepository
import com.example.data.repository.GeminiChatRepository
import com.example.data.repository.LocalSqlCacheRepository
import com.example.data.repository.StaffTrackRepository
import com.example.data.repository.VerificationSuiteRepository
import com.example.domain.LeaveValidationEngine
import com.example.domain.RequestStatus
import com.example.ui.screens.AiCopilotScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.HrHubScreen
import com.example.ui.screens.LeaveRequestSheet
import com.example.ui.screens.ManagerInboxScreen
import com.example.ui.screens.MyLeaveScreen
import com.example.ui.screens.NotificationCenterSheet
import com.example.ui.screens.WorkspaceOnboardingScreen
import com.example.ui.theme.StaffTrackTheme
import com.example.viewmodel.MainTab
import com.example.viewmodel.StaffTrackViewModel
import com.example.viewmodel.WorkspaceLoadState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            StaffTrackTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StaffTrackRoot()
                }
            }
        }
    }
}

@Composable
private fun StaffTrackRoot(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener drives navigation */ })
    } else {
        key(user.uid) {
            AuthenticatedStaffTrackShell(
                currentUserId = user.uid,
                currentUserDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "Staff Member"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedStaffTrackShell(
    currentUserId: String,
    currentUserDisplayName: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: StaffTrackViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val repo = StaffTrackRepository(db)
                val localDb = StaffTrackDatabase.getInstance(app)
                StaffTrackViewModel(
                    repository = repo,
                    aiParser = AiLeaveParserRepository(),
                    verificationSuite = VerificationSuiteRepository(repo),
                    localSqlRepo = LocalSqlCacheRepository(localDb.staffTrackDao()),
                    chatRepository = GeminiChatRepository(),
                    currentUserId = currentUserId,
                    currentUserDisplayName = currentUserDisplayName
                )
            }
        }
    )

    val workspaceState by viewModel.workspaceState.collectAsStateWithLifecycle()
    val sqlStats by viewModel.sqlStats.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedHrSubTab by viewModel.selectedHrSubTab.collectAsStateWithLifecycle()
    val actionLoading by viewModel.actionLoading.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val aiParseState by viewModel.aiParseState.collectAsStateWithLifecycle()
    val verificationCases by viewModel.verificationCases.collectAsStateWithLifecycle()
    val isRunningTests by viewModel.isRunningTests.collectAsStateWithLifecycle()
    val reportYear by viewModel.reportYear.collectAsStateWithLifecycle()
    val reportMonth by viewModel.reportMonth.collectAsStateWithLifecycle()
    val reportTeamFilter by viewModel.reportTeamFilter.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val selectedPersona by viewModel.selectedPersona.collectAsStateWithLifecycle()
    val streamingReply by viewModel.streamingReply.collectAsStateWithLifecycle()
    val isChatStreaming by viewModel.isChatStreaming.collectAsStateWithLifecycle()
    val chatError by viewModel.chatError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showRequestSheet by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Predictive back handling for secondary tabs/sheets
    BackHandler(enabled = showRequestSheet || showNotificationsSheet || selectedTab != MainTab.MY_LEAVE) {
        when {
            showRequestSheet -> {
                showRequestSheet = false
                viewModel.resetAiParseState()
            }
            showNotificationsSheet -> showNotificationsSheet = false
            selectedTab != MainTab.MY_LEAVE -> viewModel.selectTab(MainTab.MY_LEAVE)
        }
    }

    when (val state = workspaceState) {
        WorkspaceLoadState.Loading -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading StaffTrack workspace...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        WorkspaceLoadState.NeedsOnboarding -> {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    WorkspaceOnboardingScreen(
                        defaultDisplayName = currentUserDisplayName,
                        isLoading = actionLoading,
                        onCreateWorkspace = viewModel::createWorkspace,
                        onJoinWorkspace = viewModel::joinWorkspace,
                        onSignOut = { signOutFromGoogle(credentialManager, scope) }
                    )
                }
            }
        }

        is WorkspaceLoadState.Error -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Unable to sync workspace",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is WorkspaceLoadState.Ready -> {
            val currentMember = state.currentMember
            val unreadCount = state.notifications.count { !it.read }
            val pendingForMeCount = if (currentMember != null) {
                state.leaveRequests.count { req ->
                    req.parsedStatus == RequestStatus.PENDING &&
                        LeaveValidationEngine.canDecideForTeam(currentMember, req.teamId)
                }
            } else 0

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavRail = maxWidth >= 600.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavRail) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight(),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            MainTab.entries.forEach { tab ->
                                val (selectedIcon, unselectedIcon, labelRes) = tabSpec(tab)
                                NavigationRailItem(
                                    selected = selectedTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        TabBadgeIcon(
                                            icon = if (selectedTab == tab) selectedIcon else unselectedIcon,
                                            label = stringResource(labelRes),
                                            badgeCount = if (tab == MainTab.APPROVALS) pendingForMeCount else 0
                                        )
                                    },
                                    label = { Text(stringResource(labelRes)) }
                                )
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.weight(1f),
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        topBar = {
                            TopAppBar(
                                title = {
                                    Column {
                                        Text(
                                            text = state.workspace.name,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = "StaffTrack · ${currentMember?.parsedRole?.label ?: "Member"}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                actions = {
                                    IconButton(
                                        onClick = { showNotificationsSheet = true },
                                        modifier = Modifier.testTag("notifications_button")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (unreadCount > 0) {
                                                    Badge { Text(unreadCount.coerceAtMost(99).toString()) }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Notifications,
                                                contentDescription = stringResource(R.string.cd_notifications)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { signOutFromGoogle(credentialManager, scope) },
                                        modifier = Modifier.testTag("sign_out_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                                            contentDescription = stringResource(R.string.cd_sign_out)
                                        )
                                    }
                                }
                            )
                        },
                        bottomBar = {
                            if (!useNavRail) {
                                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                    MainTab.entries.forEach { tab ->
                                        val (selectedIcon, unselectedIcon, labelRes) = tabSpec(tab)
                                        NavigationBarItem(
                                            selected = selectedTab == tab,
                                            onClick = { viewModel.selectTab(tab) },
                                            icon = {
                                                TabBadgeIcon(
                                                    icon = if (selectedTab == tab) selectedIcon else unselectedIcon,
                                                    label = stringResource(labelRes),
                                                    badgeCount = if (tab == MainTab.APPROVALS) pendingForMeCount else 0
                                                )
                                            },
                                            label = { Text(stringResource(labelRes)) },
                                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        },
                        floatingActionButton = {
                            if (selectedTab == MainTab.MY_LEAVE && currentMember != null) {
                                ExtendedFloatingActionButton(
                                    onClick = { showRequestSheet = true },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = stringResource(R.string.cd_request_leave)
                                        )
                                    },
                                    text = { Text(stringResource(R.string.action_request_leave)) },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.testTag("request_leave_fab")
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (currentMember == null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                val contentModifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 840.dp)

                                AnimatedContent(
                                    targetState = selectedTab,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "main_tab_transition"
                                ) { tab ->
                                    when (tab) {
                                        MainTab.MY_LEAVE -> MyLeaveScreen(
                                            member = currentMember,
                                            myRequests = state.leaveRequests.filter { it.employeeUid == currentMember.uid },
                                            modifier = contentModifier
                                        )

                                        MainTab.APPROVALS -> ManagerInboxScreen(
                                            currentMember = currentMember,
                                            teams = state.teams,
                                            members = state.members,
                                            allRequests = state.leaveRequests,
                                            isBusy = actionLoading,
                                            onApprove = viewModel::approveRequest,
                                            onDecline = viewModel::declineRequest,
                                            modifier = contentModifier
                                        )

                                        MainTab.AI_COPILOT -> AiCopilotScreen(
                                            selectedPersona = selectedPersona,
                                            messages = chatMessages,
                                            streamingReply = streamingReply,
                                            isStreaming = isChatStreaming,
                                            errorMessage = chatError,
                                            onSelectPersona = viewModel::selectChatPersona,
                                            onSendMessage = viewModel::sendChatMessage,
                                            onClearHistory = viewModel::clearChatHistory,
                                            modifier = contentModifier
                                        )

                                        MainTab.HR_HUB -> HrHubScreen(
                                            workspace = state.workspace,
                                            currentMember = currentMember,
                                            teams = state.teams,
                                            members = state.members,
                                            requests = state.leaveRequests,
                                            auditLogs = state.auditLogs,
                                            sqlStats = sqlStats,
                                            selectedSubTab = selectedHrSubTab,
                                            reportYear = reportYear,
                                            reportMonth = reportMonth,
                                            reportTeamFilter = reportTeamFilter,
                                            verificationCases = verificationCases,
                                            isRunningTests = isRunningTests,
                                            isBusy = actionLoading,
                                            onSelectSubTab = viewModel::selectHrSubTab,
                                            onShiftMonth = viewModel::shiftReportMonth,
                                            onSelectTeamFilter = viewModel::setReportTeamFilter,
                                            onExportCsv = {
                                                val csv = viewModel.buildCsvForCurrentFilter()
                                                if (csv != null) {
                                                    shareMonthlyCsv(
                                                        context = context,
                                                        csvContent = csv,
                                                        monthPrefix = LeaveValidationEngine.monthPrefix(reportYear, reportMonth),
                                                        onError = viewModel::showMessage
                                                    )
                                                }
                                            },
                                            onCreateTeam = viewModel::createTeam,
                                            onUpdateMemberRoleAndTeams = viewModel::updateMemberRoleAndTeams,
                                            onAdjustBalance = viewModel::adjustMemberBalance,
                                            onCopyInviteCode = { code ->
                                                copyToClipboard(context, code)
                                                viewModel.showMessage("Invite code $code copied to clipboard.")
                                            },
                                            onRunVerificationSuite = viewModel::runVerificationSuite,
                                            modifier = contentModifier
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showRequestSheet && currentMember != null) {
                    LeaveRequestSheet(
                        member = currentMember,
                        existingRequests = state.leaveRequests,
                        aiParseState = aiParseState,
                        isSubmitting = actionLoading,
                        onParseWithAi = viewModel::parseLeaveWithAi,
                        onResetAiState = viewModel::resetAiParseState,
                        onSubmit = { type, start, end, reason, aiUsed ->
                            viewModel.submitLeaveRequest(type, start, end, reason, aiUsed) {
                                showRequestSheet = false
                            }
                        },
                        onDismiss = { showRequestSheet = false }
                    )
                }

                if (showNotificationsSheet) {
                    NotificationCenterSheet(
                        notifications = state.notifications,
                        onMarkRead = viewModel::markNotificationRead,
                        onMarkAllRead = viewModel::markAllNotificationsRead,
                        onDismiss = { showNotificationsSheet = false }
                    )
                }
            }
        }
    }
}

private fun tabSpec(tab: MainTab) = when (tab) {
    MainTab.MY_LEAVE -> Triple(Icons.Rounded.EventNote, Icons.Outlined.EventNote, R.string.tab_my_leave)
    MainTab.APPROVALS -> Triple(Icons.Rounded.FactCheck, Icons.Outlined.FactCheck, R.string.tab_approvals)
    MainTab.AI_COPILOT -> Triple(Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, R.string.tab_ai_copilot)
    MainTab.HR_HUB -> Triple(Icons.Rounded.Assessment, Icons.Outlined.Assessment, R.string.tab_hr_hub)
}

@Composable
private fun TabBadgeIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeCount: Int
) {
    BadgedBox(
        badge = {
            if (badgeCount > 0) {
                Badge { Text(badgeCount.coerceAtMost(99).toString()) }
            }
        }
    ) {
        Icon(imageVector = icon, contentDescription = label)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("StaffTrack Invite Code", text))
}

private fun shareMonthlyCsv(
    context: Context,
    csvContent: String,
    monthPrefix: String,
    onError: (String) -> Unit
) {
    try {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(reportsDir, "stafftrack_absences_$monthPrefix.csv")
        file.writeText(csvContent, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "StaffTrack Monthly Absences — $monthPrefix")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Monthly Absence CSV"))
    } catch (e: Exception) {
        onError(e.localizedMessage ?: "Unable to export CSV report.")
    }
}
