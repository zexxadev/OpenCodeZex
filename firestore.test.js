import { createRequire } from "node:module";
import { readFileSync } from "node:fs";
import { test, before, after, beforeEach } from "node:test";

const require = createRequire(import.meta.url);

function loadModule(pkg) {
  try {
    return require(pkg);
  } catch {
    const searchPaths = [
      "/usr/local/lib/node_modules",
      "/usr/local/lib/node_modules/@firebase/rules-unit-testing/node_modules"
    ];
    return require(require.resolve(pkg, { paths: searchPaths }));
  }
}

const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} = loadModule("@firebase/rules-unit-testing");
const firebase = loadModule("firebase/compat/app");
loadModule("firebase/compat/firestore");

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "user_alice_123";
const OTHER_UID = "user_bob_456";

const serverTimestamp = () => firebase.firestore.FieldValue.serverTimestamp();

let testEnv;

before(async () => {
  const [host, portStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host,
      port: Number(portStr),
      rules: readFileSync("firestore.rules", "utf8")
    }
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

function dbFor(uid) {
  const ctx = uid
    ? testEnv.authenticatedContext(uid)
    : testEnv.unauthenticatedContext();
  return ctx.firestore();
}

test("unauthenticated users are denied everywhere", async () => {
  const anonDb = dbFor(null);
  await assertFails(anonDb.doc(`users/${OWNER_UID}`).get());
  await assertFails(anonDb.collection(`users/${OWNER_UID}/app_settings`).get());
  await assertFails(anonDb.collection(`users/${OWNER_UID}/server_profiles`).get());
  await assertFails(anonDb.collection(`users/${OWNER_UID}/provider_accounts`).get());
  await assertFails(
    anonDb.doc(`users/${OWNER_UID}/app_settings/preferences`).set({
      userId: OWNER_UID,
      themePreference: "DARK",
      activeProfileId: "local-android",
      selectedAgent: "build",
      selectedProviderId: "opencode",
      selectedModelId: "big-pickle",
      setupCompleted: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    })
  );
  await assertFails(anonDb.doc("arbitrary_collection/doc1").get());
});

test("owner can create, read, and update user profile; listing /users and cross-user access denied", async () => {
  const aliceDb = dbFor(OWNER_UID);
  const bobDb = dbFor(OTHER_UID);

  await assertSucceeds(
    aliceDb.doc(`users/${OWNER_UID}`).set({
      uid: OWNER_UID,
      email: "alice@example.com",
      displayName: "Alice",
      photoUrl: "https://example.com/alice.png",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    })
  );

  await assertSucceeds(aliceDb.doc(`users/${OWNER_UID}`).get());
  await assertFails(aliceDb.collection("users").get());
  await assertFails(bobDb.doc(`users/${OWNER_UID}`).get());

  await assertSucceeds(
    aliceDb.doc(`users/${OWNER_UID}`).update({
      displayName: "Alice Updated",
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(
    aliceDb.doc(`users/${OWNER_UID}`).update({
      uid: OTHER_UID,
      updatedAt: serverTimestamp()
    })
  );
});

test("app_settings enforces schema, enum values, ownership, and immutability", async () => {
  const aliceDb = dbFor(OWNER_UID);
  const bobDb = dbFor(OTHER_UID);
  const settingsRef = aliceDb.doc(`users/${OWNER_UID}/app_settings/preferences`);

  await assertSucceeds(
    settingsRef.set({
      userId: OWNER_UID,
      themePreference: "MATERIAL_GLASS",
      activeProfileId: "local-android",
      selectedAgent: "build",
      selectedProviderId: "opencode",
      selectedModelId: "qwen3-coder",
      setupCompleted: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    })
  );

  await assertSucceeds(settingsRef.get());
  await assertSucceeds(aliceDb.collection(`users/${OWNER_UID}/app_settings`).get());

  await assertSucceeds(
    settingsRef.update({
      themePreference: "MIUIX",
      selectedModelId: "big-pickle",
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(
    settingsRef.update({
      themePreference: "NEON_PURPLE",
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(
    settingsRef.update({
      extraField: "hack",
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(
    settingsRef.update({
      userId: OTHER_UID,
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(bobDb.doc(`users/${OWNER_UID}/app_settings/preferences`).get());
  await assertFails(bobDb.collection(`users/${OWNER_UID}/app_settings`).get());
  await assertFails(bobDb.doc(`users/${OWNER_UID}/app_settings/preferences`).delete());
});

test("server_profiles CRUD & validation works for owner and fails for other users", async () => {
  const aliceDb = dbFor(OWNER_UID);
  const bobDb = dbFor(OTHER_UID);
  const profileRef = aliceDb.doc(`users/${OWNER_UID}/server_profiles/preset-termux`);

  await assertSucceeds(
    profileRef.set({
      userId: OWNER_UID,
      name: "Lokal HP (Termux)",
      baseUrl: "http://127.0.0.1:4096",
      username: "opencode",
      password: "secret",
      directory: "/workspace",
      mode: "REMOTE_SERVER",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${OWNER_UID}/server_profiles`).get());
  await assertSucceeds(
    profileRef.update({
      name: "Termux Dev",
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(
    profileRef.update({
      name: "",
      updatedAt: serverTimestamp()
    })
  );
  await assertFails(
    profileRef.update({
      mode: "CLOUD_SANDBOX",
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(bobDb.doc(`users/${OWNER_UID}/server_profiles/preset-termux`).get());
  await assertFails(
    bobDb.doc(`users/${OWNER_UID}/server_profiles/rogue`).set({
      userId: OWNER_UID,
      name: "Rogue",
      baseUrl: "http://evil",
      username: "",
      password: "",
      directory: "",
      mode: "REMOTE_SERVER",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    })
  );

  await assertSucceeds(profileRef.delete());
});

test("provider_accounts validates API format, customFlag, cachedModels size, and ownership", async () => {
  const aliceDb = dbFor(OWNER_UID);
  const bobDb = dbFor(OTHER_UID);
  const accRef = aliceDb.doc(`users/${OWNER_UID}/provider_accounts/opencode`);

  await assertSucceeds(
    accRef.set({
      userId: OWNER_UID,
      providerId: "opencode",
      name: "OpenCode Zen",
      baseUrl: "https://opencode.ai/zen/v1",
      apiKey: "oc-secret-token",
      defaultModelId: "qwen3-coder",
      apiFormat: "OPENAI_COMPATIBLE",
      envVarName: "OPENCODE_API_KEY",
      customFlag: false,
      cachedModels: ["big-pickle", "qwen3-coder"],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    })
  );

  await assertSucceeds(aliceDb.collection(`users/${OWNER_UID}/provider_accounts`).get());
  await assertSucceeds(
    accRef.update({
      apiKey: "oc-rotated-token",
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(
    accRef.update({
      apiFormat: "GRAPHQL",
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(
    aliceDb.doc(`users/${OWNER_UID}/provider_accounts/custom_bad`).set({
      userId: OWNER_UID,
      providerId: "custom_bad",
      name: "Custom Bad",
      baseUrl: "https://api.example.com/v1",
      apiKey: "sk-123",
      defaultModelId: "gpt-4o-mini",
      apiFormat: "OPENAI_COMPATIBLE",
      envVarName: "CUSTOM_API_KEY",
      isCustom: true,
      cachedModels: [],
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    })
  );

  await assertFails(bobDb.doc(`users/${OWNER_UID}/provider_accounts/opencode`).get());
  await assertSucceeds(accRef.delete());
});
