# StaffTrack — Enterprise Leave & Absence Management (Android)

**StaffTrack** is a native Android application built with **Kotlin** and **Jetpack Compose (Material 3)** for **StrainX Bioworks**. It replaces fragmented spreadsheets and email threads with a single-record leave workflow featuring **atomic Firestore approval transactions**, **AI-assisted natural language entry**, a **context-aware multi-turn Gemini AI Copilot**, **zero-trust role-based access control**, **dual cloud + local SQLite persistence**, and **monthly HR absence reporting with CSV export**.

---

## Table of Contents

1. [Core Features](#core-features)
2. [How It Works (Architecture & Flows)](#how-it-works-architecture--flows)
3. [Data Model (Cloud Firestore + Room SQLite)](#data-model-cloud-firestore--room-sqlite)
4. [Security & Role-Based Access Control](#security--role-based-access-control)
5. [Tech Stack](#tech-stack)
6. [Project Structure](#project-structure)
7. [How to Start & Run](#how-to-start--run)
8. [End-to-End Walkthrough](#end-to-end-walkthrough)
9. [Running Automated Tests](#running-automated-tests)

---

## Core Features

### 1. Google Sign-In & Workspace Onboarding
- **Passwordless Google Sign-In** via Android **Jetpack Credential Manager** (`GetSignInWithGoogleOption`) backed by Firebase Authentication, with automatic silent sign-in for returning users.
- **Create Workspace (HR Admin)**: Initializes the **StrainX Bioworks** workspace with a generated 6-character invite code and seeds three default teams:
  - *Bioprocess Lab*
  - *Genomics R&D*
  - *Clinical Ops*
- **Join Workspace (Employee)**: New team members enter a 6-character invite code and choose their team to join with default leave allowances (15 Annual, 10 Sick, 5 Personal days).

### 2. My Leave Tab (Employee Portal)
- **Biotech Hero Banner & Animated Radial Gauges**: Live visual meters showing remaining **Annual**, **Sick**, **Personal**, and used **Unpaid** leave balances.
- **Request Leave Bottom Sheet**:
  - **Gemini AI Smart Fill**: Type a natural-language phrase (e.g., *"Annual leave on 23 and 24 November 2026"*) and `gemini-3.8-flash` (strict JSON schema output via Firebase AI Logic) extracts the leave category and inclusive start/end dates—or asks a polite clarification question if vague. Human review is always required before submitting.
  - **Material 3 DateRangePicker** & Quick Category Chips.
  - **Pre-Write Validation Engine**: Automatically blocks inverted date ranges (`endDate < startDate`), overlapping requests against existing `PENDING`/`APPROVED` leave, and requests exceeding remaining paid balances.
- **Personal Request History**: Real-time list of submitted requests with status pills (`Pending`, `Approved`, `Declined`) and manager review comments.

### 3. Approvals Tab (Manager & HR Inbox)
- **Team-Scoped Queue**: Displays pending requests only for teams the signed-in Manager is authorized to manage (or all teams for HR Admin).
- **Live Balance Impact Preview**: Shows current balance vs. post-approval remaining days before deciding.
- **Atomic Approvals & Declines**: Optional manager comments, one-tap Approve/Decline backed by Firestore transactions, and instant audit + employee notification creation.

### 4. AI Copilot Tab (Multi-Turn Streaming Assistant)
- Powered by **Firebase AI Logic (`Firebase.ai`)** with live token streaming—**no client-side API keys required**.
- **Three Specialized Personas** grounded in your real-time workspace snapshot, live leave balances, and personal request history:
  1. **Leave Advisor** (`gemini-3.8-flash`) — Check balances, avoid date overlaps, draft leave notes, and understand policy rules.
  2. **Manager Coach** (`gemini-3.8-flash`) — Evaluate pending requests, plan lab shift coverage, and draft constructive approval/decline comments.
  3. **HR Analyst** (`gemini-3.5-flash-lite`) — Analyze monthly absence trends, Pilot KPIs, and audit traceability.
- **Persistent Chat History**: Conversations are saved locally per workspace and persona in the on-device **Room SQLite** database (`stafftrack_sql.db`) and can be cleared anytime.

### 5. HR Hub Tab (Reporting, Audit, Admin & Integrity Tests)
Organized into four sub-views:
- **Report**:
  - Month selector (`YYYY-MM`), **Pilot KPI tiles** (Average Approval Turnaround, Balance Corrections, Total Approved Days, Pending Queue), and a per-employee monthly breakdown.
  - **CSV Export**: Generates an RFC-4180 CSV absence report and shares it via Android `FileProvider` (zero storage permissions required).
  - **Dual Storage Sync Card**: Displays live Room SQLite mirror status (cached leaves, cached audit entries, and saved AI Copilot messages).
- **Audit Log**: Immutable, chronological trail of every approval, decline, and HR balance adjustment—showing actor, timestamp, before/after balance deltas, and comments.
- **Org & Roles**: Share workspace invite code, create new teams, assign roles (`Employee`, `Manager`, `HR Admin`) and managed teams, and perform audited manual leave balance adjustments.
- **Integrity Tests (Live 5-Scenario Verification Suite)**: Run live atomic transaction & concurrency verification tests directly against Firestore using isolated synthetic records that automatically clean up after execution.

### 6. In-App Notification Center
- Accessible from the top bar bell icon with an unread badge count.
- Receives instant approval and decline notices with manager comments, supporting individual mark-as-read and mark-all-read actions.

### 7. Adaptive Material 3 UI
- Custom **Biotech Teal & Deep Slate** color system, bundled typography (**Space Grotesk**, **Plus Jakarta Sans**, **JetBrains Mono**), and custom adaptive launcher icon.
- Edge-to-edge layout (`enableEdgeToEdge()`) with responsive navigation: **Bottom `NavigationBar`** on phones (`< 600dp`) and **`NavigationRail`** on tablets/foldables (`>= 600dp`).

---

## How It Works (Architecture & Flows)

StaffTrack follows **MVVM + Unidirectional Data Flow (UDF)** with Kotlin Coroutines and `StateFlow`:

```
┌──────────────────────────────────────────────────────────┐
│  Jetpack Compose UI (Material 3, Adaptive Nav)           │
│  Auth | Onboarding | MyLeave | Approvals | Copilot | HR  │
└───────────────────────────┬──────────────────────────────┘
                            │ StateFlow / Events
┌───────────────────────────▼──────────────────────────────┐
│                 StaffTrackViewModel                      │
└───────┬───────────────────┬──────────────────┬───────────┘
        │                   │                  │
        ▼                   ▼                  ▼
┌───────────────┐  ┌────────────────┐  ┌───────────────────┐
│ StaffTrack    │  │ LocalSqlCache  │  │ Firebase AI Logic │
│ Repository    │  │ Repository     │  │ (App Check auth)  │
│ (Firestore    │  │ (Room SQLite   │  │ - AiLeaveParser   │
│  Snapshots &  │  │  stafftrack_   │  │ - GeminiChatRepo  │
│  Transactions)│  │  sql.db)       │  │                   │
└───────┬───────┘  └────────────────┘  └───────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────┐
│  Cloud Firestore Enterprise + Firebase Auth + App Check  │
│  Enforced by Zero-Trust firestore.rules                  │
└──────────────────────────────────────────────────────────┘
```

### Dual Storage Strategy
1. **Cloud Firestore Enterprise (Source of Truth)**: Handles multi-user real-time synchronization (`.snapshots()`), role-based access control, atomic approvals, audit logs, and notifications across devices.
2. **Room SQLite (`stafftrack_sql.db`, Local Cache & Chat Store)**: Mirrors active workspace leave requests and audit logs on-device for fast offline visibility and durably stores multi-turn AI Copilot conversations per workspace and persona.

### Atomic Approval Transaction Flow
When a Manager or HR Admin taps **Approve**, `StaffTrackRepository.approveLeaveRequest()` executes a single Firestore `runTransaction` block:
1. Reads the target `LeaveRequest`, the employee's `Member` document, and the acting reviewer's `Member` document.
2. Verifies **team authorization** (HR Admin or Manager whose `managedTeamIds` includes the request's `teamId`).
3. Verifies **idempotency** (`status == PENDING`). Retried clicks return `AlreadyProcessed` without double-deducting.
4. Verifies **balance sufficiency** (e.g., `annualDays >= dayCount`; Unpaid leave increments `unpaidUsedDays`).
5. Atomically updates the request to `APPROVED`, deducts the employee's leave balance, inserts an immutable `AuditLog` document, and writes a `NotificationItem` for the employee.

If two managers approve competing requests concurrently, Firestore retries the conflicting transaction so balance can never drop below zero.

### Zero-Key AI Security
- `MainActivity.onCreate()` initializes **Firebase App Check** (Play Integrity / Debug provider) **before** any AI or database calls.
- Both `AiLeaveParserRepository` and `GeminiChatRepository` use `Firebase.ai.generativeModel(...)` so no Gemini API key is ever bundled in the APK or `.env` file.

---

## Data Model (Cloud Firestore + Room SQLite)

### Cloud Firestore Collections
| Path | Purpose |
| --- | --- |
| `/userWorkspaces/{uid}` | Maps a signed-in user to their active `workspaceId` |
| `/inviteCodes/{code}` | 6-character lookup resolving an invite code to a `workspaceId` |
| `/workspaces/{wsId}` | Workspace metadata, name, invite code, and `memberUids` |
| `/workspaces/{wsId}/teams/{teamId}` | Organization teams (*Bioprocess Lab*, *Genomics R&D*, *Clinical Ops*, custom) |
| `/workspaces/{wsId}/members/{uid}` | Member profile, role, team assignment, `managedTeamIds`, and leave balances |
| `/workspaces/{wsId}/leaveRequests/{reqId}` | Leave requests (`PENDING`, `APPROVED`, `DECLINED`), dates, `dayCount`, `aiAssisted` flag |
| `/workspaces/{wsId}/auditLogs/{logId}` | Immutable audit trail of approvals, declines, and HR balance edits |
| `/workspaces/{wsId}/notifications/{notifId}` | In-app notifications per `recipientUid` with `read` state |

### Local Room SQLite Tables (`stafftrack_sql.db`)
| Table | Purpose |
| --- | --- |
| `cached_leave_requests` | Local SQLite mirror of workspace leave requests |
| `cached_audit_logs` | Local SQLite mirror of workspace audit entries |
| `chat_messages` | Persistent multi-turn AI Copilot conversation history keyed by `workspaceId` and `persona` |

---

## Security & Role-Based Access Control

Deployed in `firestore.rules` and verified against the Firestore Emulator:
- **Zero-Trust Default Deny**: Every unauthenticated request and unmatched path is denied.
- **No Privilege Escalation**: Employees cannot self-promote to `MANAGER` or `HR_ADMIN`, nor directly modify their own leave balances.
- **Team-Scoped Approval Enforcement**: Managers can only transition requests belonging to teams in their `managedTeamIds`.
- **Terminal State Locking**: Once a leave request is `APPROVED` or `DECLINED`, its status cannot be overwritten or re-processed.
- **Immutable Audit Trail**: `auditLogs` are append-only for real records (`update` and `delete` are blocked; cleanup is restricted strictly to `syntheticTestRecord == true` documents created by HR Admin during integrity tests).

---

## Tech Stack

- **Language**: Kotlin (JVM target 11, Min SDK 24, Target/Compile SDK 36)
- **UI**: Jetpack Compose, Material 3, Material Icons Extended, Adaptive Navigation
- **Architecture**: MVVM, `ViewModel`, `StateFlow`, Kotlin Coroutines & Flow
- **Cloud Backend**: Firebase Firestore Enterprise (named database), Firebase Authentication, Firebase App Check
- **Authentication**: Android Jetpack Credential Manager (`GetSignInWithGoogleOption`)
- **Generative AI**: Firebase AI Logic (`com.google.firebase:firebase-ai`) — `gemini-3.8-flash` & `gemini-3.5-flash-lite`
- **Local SQL Storage**: AndroidX Room (`room-runtime`, `room-ktx`, KSP `room-compiler`)
- **Testing**: JUnit 4, Robolectric, Kotlin Coroutines Test, Firebase Rules Unit Testing (`@firebase/rules-unit-testing` + Node test runner)

---

## Project Structure

```text
.
├── app/
│   ├── build.gradle.kts                     # Dependencies, Room KSP, Firebase BOM, Signing
│   ├── google-services.json                 # Firebase Android configuration
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml          # Permissions, MainActivity, FileProvider
│       │   ├── java/com/example/
│       │   │   ├── MainActivity.kt          # App Check init, edge-to-edge adaptive shell, CSV share
│       │   │   ├── domain/
│       │   │   │   ├── StaffTrackModels.kt          # Domain & Firestore models, enums, states
│       │   │   │   └── LeaveValidationEngine.kt     # Date math, overlap/balance guards, KPIs, CSV
│       │   │   ├── data/
│       │   │   │   ├── firebase/
│       │   │   │   │   ├── AppCheckHelper.kt        # Play Integrity / Debug App Check setup
│       │   │   │   │   ├── GoogleAuthHelper.kt      # Credential Manager Google Sign-In flow
│       │   │   │   │   └── FirestoreErrorHandler.kt # Structured JSON diagnostics
│       │   │   │   ├── local/
│       │   │   │   │   ├── StaffTrackEntities.kt    # Room tables (leaves, audits, chat)
│       │   │   │   │   ├── StaffTrackDao.kt         # SQL queries & Flow observers
│       │   │   │   │   └── StaffTrackDatabase.kt    # Room singleton (stafftrack_sql.db)
│       │   │   │   └── repository/
│       │   │   │       ├── StaffTrackRepository.kt      # Firestore streams & atomic txns
│       │   │   │       ├── LocalSqlCacheRepository.kt   # Room mirror & chat persistence
│       │   │   │       ├── AiLeaveParserRepository.kt   # Gemini structured JSON form filler
│       │   │   │       ├── GeminiChatRepository.kt      # Streaming multi-persona AI Copilot
│       │   │   │       └── VerificationSuiteRepository.kt # 5-test live concurrency suite
│       │   │   ├── viewmodel/
│       │   │   │   └── StaffTrackViewModel.kt       # App state, intents, SQL sync, chat stream
│       │   │   └── ui/
│       │   │       ├── theme/                       # Biotech Teal palette & bundled fonts
│       │   │       ├── components/StaffTrackComponents.kt
│       │   │       └── screens/
│       │   │           ├── AuthScreen.kt
│       │   │           ├── WorkspaceOnboardingScreen.kt
│       │   │           ├── MyLeaveScreen.kt
│       │   │           ├── LeaveRequestSheet.kt
│       │   │           ├── ManagerInboxScreen.kt
│       │   │           ├── AiCopilotScreen.kt
│       │   │           ├── HrHubScreen.kt
│       │   │           ├── HrAdminAndTestsSections.kt
│       │   │           └── NotificationSheet.kt
│       │   └── res/
│       │       ├── font/                    # Bundled Space Grotesk, Plus Jakarta Sans, JetBrains Mono
│       │       ├── drawable/                # Hero banner & adaptive launcher icon foreground/background
│       │       ├── mipmap-*/                # Custom raster launcher icons
│       │       ├── xml/file_paths.xml       # FileProvider path for CSV export
│       │       └── values/                  # strings.xml & firebase_applet_config.xml
│       └── test/java/com/example/
│           └── LeaveValidationEngineTest.kt # Local JVM unit tests
├── firebase-blueprint.json                  # Firestore schema blueprint
├── firebase.json                            # Firestore rules & emulator config
├── firestore.rules                          # Zero-trust RBAC security rules (deployed)
├── firestore.test.js                        # Emulator security rule test suite (5/5 passing)
└── settings.gradle.kts
```

---

## How to Start & Run

### Option A: Run Directly in Google AI Studio (Recommended)
1. Open the **Streaming Android Emulator** preview pane in AI Studio — the app builds and installs automatically (`applicationId = com.aistudio.stafftrack.bxk4mv`).
2. Firebase Auth, Firestore Enterprise, App Check, and Firebase AI Logic are already provisioned and linked—no manual API key entry is required.
3. Click **Continue with Google** on the sign-in screen to begin.

### Option B: Build / Export for Android Studio or a Physical Device
1. Use the AI Studio settings menu to **Export as ZIP**, **Push to GitHub**, or **Download APK / AAB**.
2. To build locally via Gradle (requires JDK 11+ and Android SDK 36):
   ```bash
   gradle :app:assembleDebug
   ```
3. Install the generated APK from `app/build/outputs/apk/debug/` onto an Android device or emulator running **Android 7.0 (API 24) or higher** with Google Play Services.
4. *(Note)* If deploying to your own external Firebase project, register your signing certificate's SHA-1/SHA-256 fingerprint in Firebase Console, replace `app/google-services.json`, and deploy `firestore.rules`.

---

## End-to-End Walkthrough

Follow these steps after launching StaffTrack to test every capability:

1. **Sign In**: Tap **Continue with Google** on the Welcome screen.
2. **Initialize Workspace**: Choose **Create Workspace (HR Admin)** to launch **StrainX Bioworks** with default teams (*Bioprocess Lab*, *Genomics R&D*, *Clinical Ops*) and a 6-character invite code.
3. **Submit an AI-Assisted Leave Request**:
   - On the **My Leave** tab, tap **Request Leave**.
   - In the **AI Smart Fill** box, type: `Annual leave on 23 and 24 November 2026` and tap **Parse with AI**.
   - Confirm the extracted dates and category, optionally add a note, and tap **Submit Request**.
4. **Approve / Decline Atomically**:
   - Switch to the **Approvals** tab, review the pending request and its balance impact preview, add a manager comment, and tap **Approve** (or **Decline**).
   - Observe the balance gauge update immediately on **My Leave**.
5. **Inspect Notifications**:
   - Tap the **Bell** icon in the top bar to view the delivered decision notice with your manager comment, and mark it as read.
6. **Chat with the AI Copilot**:
   - Open the **AI Copilot** tab, choose a persona (**Leave Advisor**, **Manager Coach**, or **HR Analyst**), tap a quick prompt or type a question (e.g., *"How many annual leave days do I have left and how should I plan a 3-day break?"*), and watch the streamed response grounded in your live data.
7. **Explore HR Hub & Run Live Integrity Tests**:
   - Open the **HR Hub** tab:
     - **Report**: View Pilot KPIs, monthly absence breakdown, Room SQLite sync counts, and tap **Export CSV**.
     - **Audit**: Inspect the immutable audit trail entry for your approval.
     - **Org**: Copy the workspace invite code, add a team, or edit roles/balances.
     - **Tests**: Tap **Run All 5 Integrity Tests** to execute live Firestore retry idempotency, concurrent approval race, overlap/date-order guard, unauthorized manager denial, and notification delivery tests with automatic cleanup.

---

## Running Automated Tests

### 1. Local JVM Unit Tests (Validation Engine, KPIs & CSV)
```bash
gradle :app:testDebugUnitTest
```
Covers ISO date math, inclusive day counts, overlap detection, inverted date rejection, paid vs. unpaid balance checks, manager authorization rules, Pilot KPI computation, and RFC-4180 CSV escaping (`LeaveValidationEngineTest.kt`).

### 2. Firestore Security Rules Verification (Emulator)
Verify all 5 zero-trust RBAC scenarios (unauthenticated denial, employee self-escalation prevention, cross-team manager blocking, immutable audit log enforcement, and authorized approval flow):
```bash
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```
*(Requires the Firebase Firestore Emulator running on port `8085`.)*

### 3. Live In-App Transaction & Concurrency Suite
Open **HR Hub → Tests** inside the running app and tap **Run All 5 Integrity Tests** to verify atomic Firestore behavior end-to-end on the live database without polluting workspace reports.
