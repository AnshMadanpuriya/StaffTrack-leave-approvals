const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HR_UID = "hr_ansh_001";
const MGR_BIO_UID = "mgr_bio_002";
const MGR_GEN_UID = "mgr_gen_003";
const EMP_BIO_UID = "emp_bio_004";
const OUTSIDER_UID = "outsider_999";
const WS_ID = "ws_strainx_001";
const TEAM_BIO = "team_bioprocess";
const TEAM_GEN = "team_genomics";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedWorkspace() {
  const created = pastDate(10000);
  const allMembers = [HR_UID, MGR_BIO_UID, MGR_GEN_UID, EMP_BIO_UID];

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("workspaces").doc(WS_ID).set({
      name: "StrainX Bioworks",
      inviteCode: "SXB492",
      createdByUid: HR_UID,
      memberUids: allMembers,
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("workspaces").doc(WS_ID).collection("teams").doc(TEAM_BIO).set({
      workspaceId: WS_ID,
      name: "Bioprocess Lab",
      code: "BIO",
      memberUids: allMembers,
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("workspaces").doc(WS_ID).collection("teams").doc(TEAM_GEN).set({
      workspaceId: WS_ID,
      name: "Genomics R&D",
      code: "GEN",
      memberUids: allMembers,
      createdAt: created,
      updatedAt: created,
    });
    const baseMember = {
      workspaceId: WS_ID,
      annualDays: 15,
      sickDays: 10,
      personalDays: 5,
      unpaidUsedDays: 0,
      annualAllowance: 15,
      sickAllowance: 10,
      personalAllowance: 5,
      syntheticTestRecord: false,
      viewerUids: allMembers,
      createdAt: created,
      updatedAt: created,
    };
    await db.collection("workspaces").doc(WS_ID).collection("members").doc(HR_UID).set({
      ...baseMember,
      uid: HR_UID,
      displayName: "Ansh HR",
      role: "HR",
      teamId: TEAM_BIO,
      teamName: "Bioprocess Lab",
      managedTeamIds: [TEAM_BIO, TEAM_GEN],
    });
    await db.collection("workspaces").doc(WS_ID).collection("members").doc(MGR_BIO_UID).set({
      ...baseMember,
      uid: MGR_BIO_UID,
      displayName: "Bio Lead",
      role: "MANAGER",
      teamId: TEAM_BIO,
      teamName: "Bioprocess Lab",
      managedTeamIds: [TEAM_BIO],
    });
    await db.collection("workspaces").doc(WS_ID).collection("members").doc(MGR_GEN_UID).set({
      ...baseMember,
      uid: MGR_GEN_UID,
      displayName: "Genomics Lead",
      role: "MANAGER",
      teamId: TEAM_GEN,
      teamName: "Genomics R&D",
      managedTeamIds: [TEAM_GEN],
    });
    await db.collection("workspaces").doc(WS_ID).collection("members").doc(EMP_BIO_UID).set({
      ...baseMember,
      uid: EMP_BIO_UID,
      displayName: "Priya Scientist",
      role: "EMPLOYEE",
      teamId: TEAM_BIO,
      teamName: "Bioprocess Lab",
      managedTeamIds: [],
      annualDays: 5,
    });
  });
}

test("Unauthenticated users are blocked from all collections", async () => {
  await seedWorkspace();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("workspaces").doc(WS_ID).get());
  await assertFails(unauthDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").get());
  await assertFails(unauthDb.collection("userWorkspaces").doc(EMP_BIO_UID).get());
});

test("Employee cannot self-escalate role or modify own leave balance", async () => {
  await seedWorkspace();
  const empDb = testEnv.authenticatedContext(EMP_BIO_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Outsider self-joining as HR must fail (only EMPLOYEE allowed on self-join)
  await assertFails(
    outsiderDb.collection("workspaces").doc(WS_ID).collection("members").doc(OUTSIDER_UID).set({
      workspaceId: WS_ID,
      uid: OUTSIDER_UID,
      displayName: "Attacker",
      role: "HR",
      teamId: TEAM_BIO,
      teamName: "Bioprocess Lab",
      managedTeamIds: [],
      annualDays: 15,
      sickDays: 10,
      personalDays: 5,
      unpaidUsedDays: 0,
      annualAllowance: 15,
      sickAllowance: 10,
      personalAllowance: 5,
      syntheticTestRecord: false,
      viewerUids: [OUTSIDER_UID],
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Existing employee cannot mutate own annualDays or role
  await assertFails(
    empDb.collection("workspaces").doc(WS_ID).collection("members").doc(EMP_BIO_UID).update({
      annualDays: 99,
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    empDb.collection("workspaces").doc(WS_ID).collection("members").doc(EMP_BIO_UID).update({
      role: "HR",
      updatedAt: pastDate(),
    })
  );
});

test("Employee can submit valid PENDING leave request and cannot spoof APPROVED status or extra keys", async () => {
  await seedWorkspace();
  const empDb = testEnv.authenticatedContext(EMP_BIO_UID).firestore();
  const allMembers = [HR_UID, MGR_BIO_UID, MGR_GEN_UID, EMP_BIO_UID];
  const now = pastDate();

  // Valid Kotlin-mirrored create payload (no `id` key, no null optional fields)
  await assertSucceeds(
    empDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").doc("req_001").set({
      workspaceId: WS_ID,
      employeeUid: EMP_BIO_UID,
      employeeName: "Priya Scientist",
      teamId: TEAM_BIO,
      teamName: "Bioprocess Lab",
      leaveType: "ANNUAL",
      startDate: "2026-11-23",
      endDate: "2026-11-24",
      dayCount: 2,
      reason: "Family trip",
      aiAssisted: true,
      status: "PENDING",
      syntheticTestRecord: false,
      viewerUids: allMembers,
      submittedAt: now,
      updatedAt: now,
    })
  );

  // Reject creating already APPROVED request
  await assertFails(
    empDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").doc("req_bad_status").set({
      workspaceId: WS_ID,
      employeeUid: EMP_BIO_UID,
      employeeName: "Priya Scientist",
      teamId: TEAM_BIO,
      teamName: "Bioprocess Lab",
      leaveType: "ANNUAL",
      startDate: "2026-11-25",
      endDate: "2026-11-26",
      dayCount: 2,
      reason: "",
      aiAssisted: false,
      status: "APPROVED",
      syntheticTestRecord: false,
      viewerUids: allMembers,
      submittedAt: now,
      updatedAt: now,
    })
  );

  // Reject ghost field / @DocumentId `id` field
  await assertFails(
    empDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").doc("req_bad_id").set({
      id: "req_bad_id",
      workspaceId: WS_ID,
      employeeUid: EMP_BIO_UID,
      employeeName: "Priya Scientist",
      teamId: TEAM_BIO,
      teamName: "Bioprocess Lab",
      leaveType: "ANNUAL",
      startDate: "2026-11-25",
      endDate: "2026-11-26",
      dayCount: 2,
      reason: "",
      aiAssisted: false,
      status: "PENDING",
      syntheticTestRecord: false,
      viewerUids: allMembers,
      submittedAt: now,
      updatedAt: now,
    })
  );

  // Reject endDate < startDate
  await assertFails(
    empDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").doc("req_bad_dates").set({
      workspaceId: WS_ID,
      employeeUid: EMP_BIO_UID,
      employeeName: "Priya Scientist",
      teamId: TEAM_BIO,
      teamName: "Bioprocess Lab",
      leaveType: "ANNUAL",
      startDate: "2026-11-26",
      endDate: "2026-11-24",
      dayCount: 2,
      reason: "",
      aiAssisted: false,
      status: "PENDING",
      syntheticTestRecord: false,
      viewerUids: allMembers,
      submittedAt: now,
      updatedAt: now,
    })
  );
});

test("Team manager authorization, terminal state locking, and unauthorized manager denial", async () => {
  await seedWorkspace();
  const empDb = testEnv.authenticatedContext(EMP_BIO_UID).firestore();
  const mgrBioDb = testEnv.authenticatedContext(MGR_BIO_UID).firestore();
  const mgrGenDb = testEnv.authenticatedContext(MGR_GEN_UID).firestore();
  const allMembers = [HR_UID, MGR_BIO_UID, MGR_GEN_UID, EMP_BIO_UID];
  const now = pastDate(5000);

  await assertSucceeds(
    empDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").doc("req_bio_1").set({
      workspaceId: WS_ID,
      employeeUid: EMP_BIO_UID,
      employeeName: "Priya Scientist",
      teamId: TEAM_BIO,
      teamName: "Bioprocess Lab",
      leaveType: "ANNUAL",
      startDate: "2026-11-23",
      endDate: "2026-11-24",
      dayCount: 2,
      reason: "Annual leave",
      aiAssisted: true,
      status: "PENDING",
      syntheticTestRecord: false,
      viewerUids: allMembers,
      submittedAt: now,
      updatedAt: now,
    })
  );

  // Genomics manager (NOT authorized for Bioprocess Lab) CANNOT approve or deduct balance
  await assertFails(
    mgrGenDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").doc("req_bio_1").update({
      status: "APPROVED",
      decidedAt: pastDate(1000),
      decidedByUid: MGR_GEN_UID,
      decidedByName: "Genomics Lead",
      decisionComment: "Unauthorized attempt",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    mgrGenDb.collection("workspaces").doc(WS_ID).collection("members").doc(EMP_BIO_UID).update({
      annualDays: 3,
      updatedAt: pastDate(1000),
    })
  );

  // Bioprocess manager (authorized for Bioprocess Lab) CAN approve, deduct balance, write audit log & notify
  await assertSucceeds(
    mgrBioDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").doc("req_bio_1").update({
      status: "APPROVED",
      decidedAt: pastDate(1000),
      decidedByUid: MGR_BIO_UID,
      decidedByName: "Bio Lead",
      decisionComment: "Approved — cover arranged",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    mgrBioDb.collection("workspaces").doc(WS_ID).collection("members").doc(EMP_BIO_UID).update({
      annualDays: 3,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    mgrBioDb.collection("workspaces").doc(WS_ID).collection("auditLogs").doc("log_1").set({
      workspaceId: WS_ID,
      requestId: "req_bio_1",
      employeeUid: EMP_BIO_UID,
      employeeName: "Priya Scientist",
      teamName: "Bioprocess Lab",
      action: "APPROVED",
      leaveType: "ANNUAL",
      daysChanged: -2,
      balanceBefore: 5,
      balanceAfter: 3,
      actorUid: MGR_BIO_UID,
      actorName: "Bio Lead",
      actorRole: "MANAGER",
      comment: "Approved — cover arranged",
      syntheticTestRecord: false,
      viewerUids: allMembers,
      createdAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    mgrBioDb.collection("workspaces").doc(WS_ID).collection("notifications").doc("notif_1").set({
      workspaceId: WS_ID,
      recipientUid: EMP_BIO_UID,
      requestId: "req_bio_1",
      title: "Leave Approved",
      body: "Annual leave (2026-11-23 to 2026-11-24) approved by Bio Lead.",
      outcome: "APPROVED",
      actorUid: MGR_BIO_UID,
      actorName: "Bio Lead",
      comment: "Approved — cover arranged",
      read: false,
      syntheticTestRecord: false,
      createdAt: pastDate(1000),
    })
  );

  // Terminal state lock: cannot re-decide an already APPROVED request
  await assertFails(
    mgrBioDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").doc("req_bio_1").update({
      status: "DECLINED",
      decidedAt: pastDate(500),
      decidedByUid: MGR_BIO_UID,
      decidedByName: "Bio Lead",
      decisionComment: "Changed mind",
      updatedAt: pastDate(500),
    })
  );

  // Audit logs cannot be modified or deleted (real records are immutable)
  await assertFails(
    mgrBioDb.collection("workspaces").doc(WS_ID).collection("auditLogs").doc("log_1").update({
      comment: "Tampered comment",
    })
  );
  await assertFails(
    mgrBioDb.collection("workspaces").doc(WS_ID).collection("auditLogs").doc("log_1").delete()
  );
});

test("Scoped Kotlin queries succeed; unfiltered and cross-user queries fail", async () => {
  await seedWorkspace();
  const empDb = testEnv.authenticatedContext(EMP_BIO_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  // Matches Kotlin queries: whereArrayContains("viewerUids", uid) & whereEqualTo("recipientUid", uid)
  await assertSucceeds(
    empDb.collection("workspaces").doc(WS_ID).collection("teams")
      .where("memberUids", "array-contains", EMP_BIO_UID).get()
  );
  await assertSucceeds(
    empDb.collection("workspaces").doc(WS_ID).collection("members")
      .where("viewerUids", "array-contains", EMP_BIO_UID).get()
  );
  await assertSucceeds(
    empDb.collection("workspaces").doc(WS_ID).collection("leaveRequests")
      .where("viewerUids", "array-contains", EMP_BIO_UID).get()
  );
  await assertSucceeds(
    empDb.collection("workspaces").doc(WS_ID).collection("auditLogs")
      .where("viewerUids", "array-contains", EMP_BIO_UID).get()
  );
  await assertSucceeds(
    empDb.collection("workspaces").doc(WS_ID).collection("notifications")
      .where("recipientUid", "==", EMP_BIO_UID).get()
  );

  // Unfiltered queries MUST fail
  await assertFails(empDb.collection("workspaces").doc(WS_ID).collection("leaveRequests").get());
  await assertFails(empDb.collection("workspaces").doc(WS_ID).collection("notifications").get());

  // Cross-user notification query MUST fail
  await assertFails(
    outsiderDb.collection("workspaces").doc(WS_ID).collection("notifications")
      .where("recipientUid", "==", EMP_BIO_UID).get()
  );
});
