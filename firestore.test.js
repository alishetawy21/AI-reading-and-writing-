const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user cannot read or write phrases or practiceSessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("phrases").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("practiceSessions").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_1").set({
      userId: ALICE_UID,
      sourceText: "Good morning",
      translatedText: "Buenos dias",
      sourceLanguage: "en-US",
      targetLanguage: "es-ES",
      ipa: "/ɡʊd ˈmɔːrnɪŋ/",
      accentTip: "Round the /ɔː/ vowel cleanly.",
      lastScore: 88,
      practiceCount: 1,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid Kotlin create, partial update, list, and delete on phrases", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Mirror PhraseRepository.savePhrase (NO @DocumentId `id` field in map)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_1").set({
      userId: ALICE_UID,
      sourceText: "The quick brown fox jumps over the lazy dog.",
      translatedText: "El zorro marron rapido salta sobre el perro perezoso.",
      sourceLanguage: "en-US",
      targetLanguage: "es-ES",
      ipa: "/ðə kwɪk braʊn fɒks/",
      accentTip: "Keep the voiced /ð/ soft with tongue tip between teeth.",
      lastScore: 0,
      practiceCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Mirror PhraseRepository.observePhrases subcollection query
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").get()
  );

  // Mirror partial score update (createdAt immutable, only allowlisted keys)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_1").update({
      lastScore: 92,
      practiceCount: 1,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject Ghost Field / shadow update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_1").update({
      lastScore: 99,
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject @DocumentId 'id' leakage in create payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_2").set({
      id: "phrase_2",
      userId: ALICE_UID,
      sourceText: "Hello world",
      translatedText: "Hola mundo",
      sourceLanguage: "en-US",
      targetLanguage: "es-ES",
      ipa: "/həˈloʊ/",
      accentTip: "Aspirate the initial h.",
      lastScore: 0,
      practiceCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject invalid score > 100
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_3").set({
      userId: ALICE_UID,
      sourceText: "Hello",
      translatedText: "Hola",
      sourceLanguage: "en-US",
      targetLanguage: "es-ES",
      ipa: "",
      accentTip: "",
      lastScore: 150,
      practiceCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject tampered future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_4").set({
      userId: ALICE_UID,
      sourceText: "Hello",
      translatedText: "Hola",
      sourceLanguage: "en-US",
      targetLanguage: "es-ES",
      ipa: "",
      accentTip: "",
      lastScore: 80,
      practiceCount: 1,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("phrases").doc("phrase_1").delete()
  );
});

test("Authenticated owner: valid practiceSessions create & list; cross-user denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("practiceSessions").doc("sess_1").set({
      userId: ALICE_UID,
      phraseText: "Je voudrais un cafe, s'il vous plait.",
      targetLanguage: "fr-FR",
      recognizedTranscript: "Je voudrais un cafe s'il vous plait",
      overallScore: 91,
      accentScore: 89,
      fluencyScore: 94,
      intonationScore: 90,
      coachSummary: "Strong nasal vowel on 'un'; soften the final 'r' in voudrais.",
      speechEngine: "Google Cloud STT + Gemini",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("practiceSessions").get()
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("practiceSessions").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("practiceSessions").doc("sess_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("practiceSessions").doc("sess_2").set({
      userId: ALICE_UID,
      phraseText: "Spoof",
      targetLanguage: "en-US",
      recognizedTranscript: "Spoof",
      overallScore: 50,
      accentScore: 50,
      fluencyScore: 50,
      intonationScore: 50,
      coachSummary: "Spoof",
      speechEngine: "Test",
      createdAt: now,
      updatedAt: now,
    })
  );
});
