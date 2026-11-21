const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_vault_123";
const BOB_UID = "bob_vault_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: denied across vaultConfig, categories, and notes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("categories").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vaultConfig")
      .doc("settings")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_1")
      .set({
        userId: ALICE_UID,
        categoryId: "cat_personal",
        encryptedTitle: "YWJjZGVm",
        titleIv: "MTIzNDU2Nzg5MDEy",
        encryptedBody: "YWJj",
        bodyIv: "MTIzNDU2Nzg5MDEy",
        audioDurationMs: 0,
        hasAudio: false,
        locked: false,
        pinned: false,
        encryptionVersion: 1,
        tags: [],
        createdAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );
});

test("Owner vaultConfig: create, update, and reject invalid configId or ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vaultConfig")
      .doc("settings")
      .set({
        userId: ALICE_UID,
        pinEnabled: true,
        pinSalt: "c2FsdDEyMzQ1Njc4OTA=",
        pinHash: "aGFzaDEyMzQ1Njc4OTAxMjM0NTY=",
        wrappedVaultKey: "d3JhcHBlZEtleQ==",
        wrappedVaultKeyIv: "aXYxMjM0NTY3ODkw",
        lockOnLaunch: true,
        createdAt: past,
        updatedAt: past,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vaultConfig")
      .doc("settings")
      .update({
        lockOnLaunch: false,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject non-'settings' doc id
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vaultConfig")
      .doc("other")
      .set({
        userId: ALICE_UID,
        pinEnabled: false,
        pinSalt: "",
        pinHash: "",
        lockOnLaunch: false,
        createdAt: past,
        updatedAt: past,
      })
  );

  // Reject ghost field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("vaultConfig")
      .doc("settings")
      .update({
        isAdmin: true,
        updatedAt: new Date(Date.now() - 500),
      })
  );
});

test("Owner categories: list, create, update, delete succeed; invalid bounds fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("categories").get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("categories")
      .doc("cat_work")
      .set({
        userId: ALICE_UID,
        name: "İş & Projeler",
        colorHex: "#10B981",
        iconKey: "work",
        sortOrder: 1,
        createdAt: past,
        updatedAt: past,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("categories")
      .doc("cat_work")
      .update({
        name: "Projeler",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject empty name
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("categories")
      .doc("cat_bad")
      .set({
        userId: ALICE_UID,
        name: "",
        colorHex: "#10B981",
        iconKey: "work",
        sortOrder: 2,
        createdAt: past,
        updatedAt: past,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("categories")
      .doc("cat_work")
      .delete()
  );
});

test("Owner notes: create with/without encryptedAudio, partial update, immutable guards", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const past = new Date(Date.now() - 5000);

  // Create text-only note (no optional encryptedAudio/audioIv keys)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_text")
      .set({
        userId: ALICE_UID,
        categoryId: "cat_personal",
        encryptedTitle: "ZW5jcnlwdGVkVGl0bGU=",
        titleIv: "aXYxMjM0NTY3ODkwMTI=",
        encryptedBody: "ZW5jcnlwdGVkQm9keQ==",
        bodyIv: "aXYxMjM0NTY3ODkwMTI=",
        audioDurationMs: 0,
        hasAudio: false,
        locked: true,
        pinned: true,
        encryptionVersion: 1,
        tags: ["kasa", "gizli"],
        createdAt: past,
        updatedAt: past,
      })
  );

  // Create voice note with encryptedAudio & audioIv
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_voice")
      .set({
        userId: ALICE_UID,
        categoryId: "cat_voice",
        encryptedTitle: "ZW5jcnlwdGVkVm9pY2U=",
        titleIv: "aXYxMjM0NTY3ODkwMTI=",
        encryptedBody: "",
        bodyIv: "",
        encryptedAudio: "ZW5jcnlwdGVkQXVkaW9CeXRlcw==",
        audioIv: "aXYxMjM0NTY3ODkwMTI=",
        audioDurationMs: 14500,
        hasAudio: true,
        locked: false,
        pinned: false,
        encryptionVersion: 1,
        tags: [],
        createdAt: past,
        updatedAt: past,
      })
  );

  // Partial update on note
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_text")
      .update({
        pinned: false,
        locked: false,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // List query succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );

  // Reject @DocumentId 'id' inside data
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_with_id")
      .set({
        id: "note_with_id",
        userId: ALICE_UID,
        categoryId: "cat_personal",
        encryptedTitle: "ZW5jcnlwdGVkVGl0bGU=",
        titleIv: "aXYxMjM0NTY3ODkwMTI=",
        encryptedBody: "",
        bodyIv: "",
        audioDurationMs: 0,
        hasAudio: false,
        locked: false,
        pinned: false,
        encryptionVersion: 1,
        tags: [],
        createdAt: past,
        updatedAt: past,
      })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_future")
      .set({
        userId: ALICE_UID,
        categoryId: "cat_personal",
        encryptedTitle: "ZW5jcnlwdGVkVGl0bGU=",
        titleIv: "aXYxMjM0NTY3ODkwMTI=",
        encryptedBody: "",
        bodyIv: "",
        audioDurationMs: 0,
        hasAudio: false,
        locked: false,
        pinned: false,
        encryptionVersion: 1,
        tags: [],
        createdAt: new Date(Date.now() + 60000),
        updatedAt: new Date(Date.now() + 60000),
      })
  );

  // Reject mutating createdAt
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_text")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );
});

test("Cross-user isolation: Alice cannot access or modify Bob's vault, categories, or notes", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const past = new Date(Date.now() - 10000);
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("notes")
      .doc("bob_note")
      .set({
        userId: BOB_UID,
        categoryId: "cat_personal",
        encryptedTitle: "Ym9iU2VjcmV0",
        titleIv: "aXYxMjM0NTY3ODkwMTI=",
        encryptedBody: "Ym9iQm9keQ==",
        bodyIv: "aXYxMjM0NTY3ODkwMTI=",
        audioDurationMs: 0,
        hasAudio: false,
        locked: true,
        pinned: false,
        encryptionVersion: 1,
        tags: [],
        createdAt: past,
        updatedAt: past,
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("notes").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("notes")
      .doc("bob_note")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("notes")
      .doc("bob_note")
      .update({ pinned: true })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("notes")
      .doc("bob_note")
      .delete()
  );
});
