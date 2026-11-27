const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_director_123";
const AGENT_A_UID = "claire_agent_456";
const AGENT_B_UID = "marc_agent_789";
const OUTSIDER_UID = "bob_outsider_999";
const STAFF_UID = "staff_moderator_001";
const SELLER_UID = "seller_particulier_777";

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

test("Unauthenticated user cannot read or write any Monty Immo collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("clients").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("clients").doc("cli_1").set({
      userId: ALICE_UID,
      name: "Claire & Thomas",
      budgetMax: 445000,
      projectStage: 2,
      portalActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("properties").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("buyer_chats").get());
  await assertFails(unauthDb.collection("agencies").limit(10).get());
  await assertFails(unauthDb.collection("agency_invites").doc("MNT-1234").get());
});

test("Owner can create and partially update ClientDossier; ghost fields and immutable mutations fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const clientRef = aliceDb.collection("users").doc(ALICE_UID).collection("clients").doc("cli_1");

  await assertSucceeds(
    clientRef.set({
      userId: ALICE_UID,
      name: "Laurent — Claire & Thomas",
      phone: "06 42 18 90 34",
      email: "thomas.laurent34@example.fr",
      situation: "Couple marié",
      status: "Actif — Visites en cours",
      projectStage: 2,
      budgetMax: 445000,
      downPayment: 95000,
      monthlyIncome: 6800,
      existingCredits: 0,
      bankApproval: "Accord de principe",
      sectors: ["Croix d'Argent (34070)", "Ovalie (34070)"],
      propertyType: "Appartement",
      minSurface: 88,
      minRooms: 3,
      essentialCriteria: ["Terrasse / Extérieur", "Parking / Garage"],
      secondaryCriteria: ["Calme absolu"],
      timelineDelai: "Sous 3 mois",
      clientMessageToAdvisor: "Proche Tram L2",
      criteriaModifiedByClient: false,
      selectedPropertyIds: ["prop_1"],
      propertyFeedback: {},
      portalActive: true,
      portalToken: "MNT-LRNT-8492-X7",
      portalExpiration: "2026-12-31",
      privateNotes: "Note confidentielle",
      timeline: [],
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    clientRef.update({
      projectStage: 3,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    clientRef.update({
      budgetMax: 460000,
      downPayment: 100000,
      sectors: ["Ovalie (34070)"],
      propertyType: "Appartement",
      minSurface: 90,
      minRooms: 3,
      essentialCriteria: ["Terrasse / Extérieur"],
      secondaryCriteria: [],
      timelineDelai: "Sous 3 mois",
      clientMessageToAdvisor: "Updated from portal",
      criteriaModifiedByClient: true,
      lastClientUpdateLabel: "Aujourd'hui 14:20",
      timeline: [{ date: "Aujourd'hui", type: "criteres", title: "Maj", detail: "Budget 460k" }],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("clients").doc("cli_ghost").set({
      userId: ALICE_UID,
      name: "Ghost",
      budgetMax: 400000,
      projectStage: 1,
      portalActive: true,
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject tampering createdAt
  await assertFails(
    clientRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Cross-user isolation prevents Bob from reading or modifying Alice's workspace", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const now = new Date(Date.now() - 5000);
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("clients").doc("cli_1").set({
      userId: ALICE_UID,
      name: "Alice Client",
      budgetMax: 400000,
      projectStage: 1,
      portalActive: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("clients").limit(100).get());
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).collection("clients").get());
  await assertFails(outsiderDb.collection("users").doc(ALICE_UID).collection("clients").limit(100).get());
  await assertFails(outsiderDb.collection("users").doc(ALICE_UID).collection("clients").doc("cli_1").get());

  const anonDb = testEnv.authenticatedContext(ALICE_UID, {
    firebase: { sign_in_provider: "anonymous" },
  }).firestore();
  await assertFails(anonDb.collection("users").doc(ALICE_UID).collection("clients").limit(10).get());
});

test("Multi-agency RBAC: Director creates agency, invites collaborator, isolates Agent A vs Agent B, prevents escalation, and keeps audit logs immutable", async () => {
  const directorDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@montyimmo.fr",
    email_verified: true,
  }).firestore();
  const agentADb = testEnv.authenticatedContext(AGENT_A_UID, {
    email: "claire@montyimmo.fr",
    email_verified: true,
  }).firestore();
  const agentBDb = testEnv.authenticatedContext(AGENT_B_UID, {
    email: "marc@montyimmo.fr",
    email_verified: true,
  }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID, {
    email: "bob@other.fr",
    email_verified: true,
  }).firestore();

  const now = new Date(Date.now() - 2000);
  const agencyId = "ag_montpellier_1";
  const inviteCode = "INV_CLAIRE_2026";

  // 1) Atomic agency + Director member + Audit log creation succeeds
  const createBatch = directorDb.batch();
  createBatch.set(directorDb.collection("agencies").doc(agencyId), {
    name: "Monty Immobilier Montpellier",
    plan: "AGENCY",
    subscriptionStatus: "ACTIVE",
    ownerUid: ALICE_UID,
    city: "Montpellier",
    inviteCode,
    directorUids: [ALICE_UID],
    memberUids: [ALICE_UID],
    createdAt: now,
    updatedAt: now,
  });
  createBatch.set(directorDb.collection("agencies").doc(agencyId).collection("members").doc(ALICE_UID), {
    agencyId,
    uid: ALICE_UID,
    email: "alice@montyimmo.fr",
    displayName: "Alice Directrice",
    role: "DIRECTOR",
    status: "ACTIVE",
    viewerUids: [ALICE_UID],
    createdAt: now,
    updatedAt: now,
  });
  createBatch.set(directorDb.collection("agencies").doc(agencyId).collection("audit_logs").doc("log_init"), {
    agencyId,
    actorUid: ALICE_UID,
    actorName: "Alice Directrice",
    actionType: "AGENCY_CREATED",
    targetLabel: "Monty Immobilier Montpellier",
    detail: "Création de l'espace multi-agences",
    viewerUids: [ALICE_UID],
    createdAt: now,
  });
  await assertSucceeds(createBatch.commit());

  // 2) Agency list without memberUids arrayContains or unbounded limit fails
  await assertFails(directorDb.collection("agencies").limit(20).get());
  await assertSucceeds(
    directorDb.collection("agencies").where("memberUids", "array-contains", ALICE_UID).limit(20).get()
  );

  // 3) Director creates single-use invite for Agent A
  await assertSucceeds(
    directorDb.collection("agency_invites").doc(inviteCode).set({
      inviteCode,
      agencyId,
      agencyName: "Monty Immobilier Montpellier",
      targetEmail: "Claire@MontyImmo.fr",
      targetName: "Claire Agent",
      role: "AGENT",
      directorUid: ALICE_UID,
      used: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cannot scrape invites collection
  await assertFails(agentADb.collection("agency_invites").limit(10).get());

  // Agent B (wrong email) CANNOT claim Claire's invite
  const hijackBatch = agentBDb.batch();
  hijackBatch.update(agentBDb.collection("agency_invites").doc(inviteCode), {
    used: true,
    claimedByUid: AGENT_B_UID,
    updatedAt: new Date(Date.now() - 1000),
  });
  hijackBatch.set(agentBDb.collection("agencies").doc(agencyId).collection("members").doc(AGENT_B_UID), {
    agencyId,
    uid: AGENT_B_UID,
    email: "marc@montyimmo.fr",
    displayName: "Marc Intrus",
    role: "AGENT",
    status: "ACTIVE",
    inviteCode,
    viewerUids: [ALICE_UID, AGENT_B_UID],
    createdAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  });
  hijackBatch.update(agentBDb.collection("agencies").doc(agencyId), {
    memberUids: [ALICE_UID, AGENT_B_UID],
    updatedAt: new Date(Date.now() - 1000),
  });
  await assertFails(hijackBatch.commit());

  // Agent A CANNOT join as DIRECTOR (privilege escalation)
  const escalateBatch = agentADb.batch();
  escalateBatch.update(agentADb.collection("agency_invites").doc(inviteCode), {
    used: true,
    claimedByUid: AGENT_A_UID,
    updatedAt: new Date(Date.now() - 1000),
  });
  escalateBatch.set(agentADb.collection("agencies").doc(agencyId).collection("members").doc(AGENT_A_UID), {
    agencyId,
    uid: AGENT_A_UID,
    email: "claire@montyimmo.fr",
    displayName: "Claire Escalade",
    role: "DIRECTOR",
    status: "ACTIVE",
    inviteCode,
    viewerUids: [ALICE_UID, AGENT_A_UID],
    createdAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  });
  escalateBatch.update(agentADb.collection("agencies").doc(agencyId), {
    memberUids: [ALICE_UID, AGENT_A_UID],
    updatedAt: new Date(Date.now() - 1000),
  });
  await assertFails(escalateBatch.commit());

  // 4) Agent A legitimately claims invite as AGENT
  const joinBatch = agentADb.batch();
  joinBatch.update(agentADb.collection("agency_invites").doc(inviteCode), {
    used: true,
    claimedByUid: AGENT_A_UID,
    updatedAt: new Date(Date.now() - 1000),
  });
  joinBatch.set(agentADb.collection("agencies").doc(agencyId).collection("members").doc(AGENT_A_UID), {
    agencyId,
    uid: AGENT_A_UID,
    email: "claire@montyimmo.fr",
    displayName: "Claire Agent",
    role: "AGENT",
    status: "ACTIVE",
    inviteCode,
    viewerUids: [ALICE_UID, AGENT_A_UID],
    createdAt: new Date(Date.now() - 1000),
    updatedAt: new Date(Date.now() - 1000),
  });
  joinBatch.update(agentADb.collection("agencies").doc(agencyId), {
    memberUids: [ALICE_UID, AGENT_A_UID],
    updatedAt: new Date(Date.now() - 1000),
  });
  await assertSucceeds(joinBatch.commit());

  // 5) Director creates two contacts: one assigned to Agent A, one assigned only to Director
  const contactARef = directorDb.collection("agencies").doc(agencyId).collection("contacts").doc("ctc_claire");
  const contactDirRef = directorDb.collection("agencies").doc(agencyId).collection("contacts").doc("ctc_dir");

  await assertSucceeds(
    contactARef.set({
      agencyId,
      createdByUid: ALICE_UID,
      assignedToUid: AGENT_A_UID,
      assignedToName: "Claire Agent",
      firstName: "Sophie",
      lastName: "Dupont",
      fullName: "Mme Dupont (Port Marianne)",
      email: "dupont@example.fr",
      phone: "0612345678",
      postalAddress: "12 Av. Raymond Dugrand, Montpellier",
      contactType: "Investisseur",
      status: "Qualifié",
      priority: "Urgent",
      budgetMin: 350000,
      budgetMax: 420000,
      sector: "Port Marianne",
      desiredPropertyType: "Appartement",
      desiredSurfaceMin: 80,
      desiredRoomsMin: 3,
      financingMode: "Crédit immobilier",
      projectTimeline: "Sous 3 mois",
      source: "Import Excel",
      notes: "Recherche T3/T4 terrasse",
      lastContactedAt: "2026-11-20 10:00",
      nextFollowUpAt: "2026-11-22 14:30",
      linkedPropertyId: "",
      linkedPropertyTitle: "",
      archived: false,
      exchangeLogs: [{ id: "ex_1", dateTime: "2026-11-20 10:00", channel: "Statut", authorName: "Alice", summary: "Création" }],
      viewerUids: [ALICE_UID, AGENT_A_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    contactDirRef.set({
      agencyId,
      createdByUid: ALICE_UID,
      assignedToUid: ALICE_UID,
      assignedToName: "Alice Directrice",
      fullName: "M. Martin (Vendeur VIP)",
      email: "martin@example.fr",
      phone: "0699887766",
      contactType: "Vendeur",
      status: "Nouveau",
      priority: "Élevé",
      budgetMax: 850000,
      sector: "Écusson",
      source: "Manuel",
      notes: "Villa confidentielle",
      archived: false,
      viewerUids: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Agent A CAN read & update status/notes/exchangeLogs/archived on assigned contact
  await assertSucceeds(
    agentADb.collection("agencies").doc(agencyId).collection("contacts")
      .where("viewerUids", "array-contains", AGENT_A_UID).limit(100).get()
  );
  await assertSucceeds(
    agentADb.collection("agencies").doc(agencyId).collection("contacts").doc("ctc_claire").update({
      status: "Actif",
      notes: "Visite confirmée vendredi",
      lastContactedAt: "2026-11-20 14:00",
      exchangeLogs: [{ id: "ex_2", dateTime: "2026-11-20 14:00", channel: "Appel", authorName: "Claire", summary: "Visite confirmée" }],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Agent A CANNOT read Director's private contact, CANNOT reassign contact, and CANNOT delete contact
  await assertFails(
    agentADb.collection("agencies").doc(agencyId).collection("contacts").doc("ctc_dir").get()
  );
  await assertFails(
    agentADb.collection("agencies").doc(agencyId).collection("contacts").doc("ctc_claire").update({
      assignedToUid: ALICE_UID,
      viewerUids: [ALICE_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    agentADb.collection("agencies").doc(agencyId).collection("contacts").doc("ctc_claire").delete()
  );

  // Outsider Bob CANNOT access anything in Alice's agency
  await assertFails(
    outsiderDb.collection("agencies").doc(agencyId).get()
  );
  await assertFails(
    outsiderDb.collection("agencies").doc(agencyId).collection("contacts").doc("ctc_claire").get()
  );

  // 6) Follow-up task creation with dueTime and completion + Audit log immutability
  const taskRef = directorDb.collection("agencies").doc(agencyId).collection("tasks").doc("tsk_1");
  await assertSucceeds(
    taskRef.set({
      agencyId,
      contactId: "ctc_claire",
      contactName: "Mme Dupont (Port Marianne)",
      createdByUid: ALICE_UID,
      assignedToUid: AGENT_A_UID,
      assignedToName: "Claire Agent",
      title: "Relancer financement courtier",
      channel: "Appel",
      dueDate: "2026-11-22",
      dueTime: "14:30",
      priority: "Haute",
      status: "À faire",
      autoGenerated: true,
      viewerUids: [ALICE_UID, AGENT_A_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    directorDb.collection("agencies").doc(agencyId).collection("tasks").doc("tsk_2").set({
      agencyId,
      contactId: "ctc_claire",
      contactName: "Mme Dupont (Port Marianne)",
      createdByUid: ALICE_UID,
      assignedToUid: AGENT_A_UID,
      assignedToName: "Claire Agent",
      title: "Priorité héritée du contact",
      channel: "Email",
      dueDate: "2026-11-23",
      priority: "Élevé",
      status: "À faire",
      viewerUids: [ALICE_UID, AGENT_A_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    agentADb.collection("agencies").doc(agencyId).collection("tasks").doc("tsk_1").update({
      status: "Terminée",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Audit logs are strictly immutable (update and delete rejected even for Director)
  await assertFails(
    directorDb.collection("agencies").doc(agencyId).collection("audit_logs").doc("log_init").update({
      detail: "Tampered history",
    })
  );
  await assertFails(
    directorDb.collection("agencies").doc(agencyId).collection("audit_logs").doc("log_init").delete()
  );
});

test("Seller workspace dossiers: owner creates Maison/Appartement with Loi ALUR docs & buyer orders; cross-user, unauth, and ghost fields denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const sellerRef = aliceDb.collection("users").doc(ALICE_UID).collection("seller_dossiers").doc("seller_prop_1");

  await assertSucceeds(
    sellerRef.set({
      userId: ALICE_UID,
      propertyId: "prop_1",
      propertyTitle: "T4 Terrasse Port Marianne",
      propertyKind: "Appartement",
      residenceType: "Résidence / Copropriété",
      sellerName: "M. & Mme Lefèvre",
      sellerEmail: "lefevre@example.fr",
      sellerPhone: "0601020304",
      mandateRef: "MND-2026-042",
      syndicName: "Nexity Montpellier",
      annualCoproCharges: 1920,
      alurWorkFundEuro: 1450,
      packPriceEuro: 29,
      paymentLinkUrl: "",
      executiveSummary: "DPE C, zéro procédure en cours, 3 PV d'AG fournis.",
      documents: [
        {
          id: "doc_alur_reglement",
          code: "ALUR_REGLEMENT",
          title: "Règlement de copropriété & EDD",
          category: "Loi ALUR",
          required: true,
          status: "Vérifié",
          uploadedByRole: "VENDEUR",
          uploadedByName: "Vendeur",
          fileName: "reglement_copro.pdf",
          mimeType: "application/pdf",
          sizeBytes: 124500,
          localVaultPath: "seller_vault/prop_1/reglement_copro.pdf",
          summaryText: "Lot n°14 + parking sous-sol, 145/10000èmes.",
          updatedAtLabel: "2026-11-20 10:00",
        },
      ],
      buyerOrders: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Un client non-staff ne peut enregistrer que PENDING_PAYMENT (jamais PAID ni DELIVERED)
  await assertSucceeds(
    sellerRef.update({
      buyerOrders: [
        {
          orderId: "ord_1",
          buyerClientId: "cli_1",
          buyerName: "Laurent — Claire & Thomas",
          buyerEmail: "thomas@example.fr",
          amountEuro: 29,
          status: "PENDING_PAYMENT",
          paymentMethodLabel: "Stripe Checkout (en attente)",
          createdAtLabel: "2026-11-20 10:15",
          deliveredAtLabel: "",
        },
      ],
      dvfValuation: {
        estimatedMidEuro: 432000,
        estimatedLowEuro: 405000,
        estimatedHighEuro: 459000,
        pricePerM2: 4595,
        valuationScore: 84,
        scoreLabel: "Très fiable",
        resolvedAddress: "Port Marianne, 34000 Montpellier",
        count50m: 2,
        count100m: 5,
        count300m: 14,
        avgM2_50m: 4680,
        avgM2_100m: 4590,
        avgM2_300m: 4480,
        dataSource: "DVF Open Data (Etalab / DGFiP)",
        computedAtLabel: "2026-11-20 10:20",
      },
      callbackRequests: [
        {
          requestId: "cb_1",
          sellerName: "M. & Mme Lefèvre",
          sellerPhone: "0601020304",
          sellerEmail: "lefevre@example.fr",
          preferredSlot: "Après-midi 14h-18h",
          projectHorizon: "Vendre sous 3 mois",
          notes: "Souhaite affiner avec la vue terrasse",
          status: "Demandée",
          createdAtLabel: "2026-11-20 10:22",
        },
      ],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field & invalid residenceType denied
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("seller_dossiers").doc("seller_bad").set({
      userId: ALICE_UID,
      propertyId: "prop_2",
      propertyTitle: "Test",
      propertyKind: "Appartement",
      residenceType: "Résidence / Copropriété",
      packPriceEuro: 29,
      documents: [],
      bypassPaywall: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Advisor can update mandate type, listingUrl, and advisor estimation on seller_dossiers
  await assertSucceeds(
    sellerRef.update({
      mandateType: "Exclusif",
      mandateDate: "2026-11-15",
      agencyFeePercent: 4.5,
      listingUrl: "https://www.seloger.com/annonces/achat/appartement/montpellier-34/123456.htm",
      listingPortal: "SeLoger",
      advisorEstimatedEuro: 435000,
      advisorValuationNotes: "Vue dégagée parc sans vis-à-vis justifiant le haut de fourchette.",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Tentative client de marquer PAID ou DELIVERED refusée ; seul staff / webhook serveur est autorisé
  await assertFails(
    sellerRef.update({
      buyerOrders: [
        {
          orderId: "ord_spoof_paid",
          buyerClientId: "cli_1",
          buyerName: "Laurent — Claire & Thomas",
          buyerEmail: "thomas@example.fr",
          amountEuro: 29,
          status: "PAID",
          paymentMethodLabel: "Bypass client",
          createdAtLabel: "2026-11-20 10:16",
          deliveredAtLabel: "2026-11-20 10:16",
        },
      ],
      updatedAt: new Date(Date.now() - 100),
    })
  );
  const staffSellerDb = testEnv.authenticatedContext(STAFF_UID, { staff: true }).firestore();
  await assertSucceeds(
    staffSellerDb.collection("users").doc(ALICE_UID).collection("seller_dossiers").doc("seller_prop_1").update({
      buyerOrders: [
        {
          orderId: "ord_1",
          buyerClientId: "cli_1",
          buyerName: "Laurent — Claire & Thomas",
          buyerEmail: "thomas@example.fr",
          amountEuro: 29,
          status: "PAID",
          paymentMethodLabel: "Stripe Webhook Vérifié",
          createdAtLabel: "2026-11-20 10:15",
          deliveredAtLabel: "2026-11-20 10:16",
        },
      ],
      updatedAt: new Date(Date.now() - 50),
    })
  );

  // Outsider cannot read or write Alice's seller dossiers
  await assertFails(outsiderDb.collection("users").doc(ALICE_UID).collection("seller_dossiers").limit(20).get());
  await assertFails(outsiderDb.collection("users").doc(ALICE_UID).collection("seller_dossiers").doc("seller_prop_1").get());
});

test("Properties: owner with advisor claim can create and update mandate, listingUrl, and DVF; non-advisor spoofing AGENCE or invalid fees fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { advisor: true }).firestore();
  const nonAdvisorDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const outsiderDb = nonAdvisorDb;
  const now = new Date(Date.now() - 2000);
  const propRef = aliceDb.collection("users").doc(ALICE_UID).collection("properties").doc("prop_mandate_1");

  await assertSucceeds(
    propRef.set({
      userId: ALICE_UID,
      title: "T4 Terrasse Port Marianne",
      sector: "Port Marianne (34000)",
      address: "12 Av. Raymond Dugrand, Montpellier",
      type: "Appartement",
      price: 449000,
      surface: 94,
      rooms: 4,
      bedrooms: 3,
      dpe: "B",
      mandateRef: "MND-2026-101",
      mandateType: "Exclusif",
      mandateDate: "2026-11-10",
      agencyFeePercent: 4.5,
      listingUrl: "https://www.bienici.com/annonce/montpellier-t4-12345",
      listingPortal: "Bien'ici",
      dvfEstimatedEuro: 442000,
      dvfEstimatedLowEuro: 418000,
      dvfEstimatedHighEuro: 465000,
      dvfScore: 86,
      dvfCriteriaSummary: "Étage avec asc. • Vue dégagée • Terrasse ≥ 15 m²",
      advisorValuationNotes: "Mandat exclusif conseillé à 449 000 € FAI.",
      latitude: 43.6003,
      longitude: 3.9041,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    propRef.update({
      price: 442000,
      mandateType: "Semi-exclusif",
      agencyFeePercent: 4.0,
      listingUrl: "https://www.seloger.com/annonces/achat/appartement/montpellier/999.htm",
      listingPortal: "SeLoger",
      dvfEstimatedEuro: 440000,
      dvfScore: 88,
      latitude: 43.6005,
      longitude: 3.9044,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Excessive agencyFeePercent (> 25) or dvfScore (> 100) rejected
  await assertFails(
    propRef.update({
      agencyFeePercent: 45,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    propRef.update({
      dvfScore: 150,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(outsiderDb.collection("users").doc(ALICE_UID).collection("properties").doc("prop_mandate_1").get());
  // Un utilisateur sans claim advisor/staff ne peut pas publier une annonce AGENCE
  await assertFails(
    nonAdvisorDb.collection("users").doc(OUTSIDER_UID).collection("properties").doc("prop_spoof_agency").set({
      userId: OUTSIDER_UID,
      title: "Fausse annonce agence",
      sector: "Antigone",
      type: "Appartement",
      price: 250000,
      surface: 55,
      rooms: 2,
      bedrooms: 1,
      dpe: "C",
      publisherType: "AGENCE",
      publicationStatus: "PUBLIEE",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("RGPD compliance & Spatial commute search: ClientDossier spatial criteria, SellerDossier RGPD consent, and Director Art. 17 erasure", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const agentDb = testEnv.authenticatedContext(AGENT_A_UID).firestore();
  const now = new Date(Date.now() - 2000);

  const clientRef = aliceDb.collection("users").doc(ALICE_UID).collection("clients").doc("cli_spatial");
  await assertSucceeds(
    clientRef.set({
      userId: ALICE_UID,
      name: "Dupont — Marc",
      budgetMax: 420000,
      projectStage: 1,
      portalActive: true,
      rgpdConsentGranted: true,
      rgpdConsentDate: "2026-11-20",
      searchCenterAddress: "Gare Saint-Roch, Montpellier",
      searchCenterLat: 43.6047,
      searchCenterLon: 3.8806,
      searchMode: "TRAVEL_TIME",
      searchRadiusKm: 8,
      searchMaxTravelMinutes: 20,
      searchTransportMode: "TRAM",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    clientRef.update({
      budgetMax: 430000,
      downPayment: 80000,
      sectors: ["Port Marianne (34000)"],
      propertyType: "Appartement",
      minSurface: 75,
      minRooms: 3,
      essentialCriteria: ["Proche Tramway"],
      secondaryCriteria: [],
      timelineDelai: "Sous 3 mois",
      clientMessageToAdvisor: "20 min max en Train/TER ou Tram",
      criteriaModifiedByClient: true,
      lastClientUpdateLabel: "Aujourd'hui 15:00",
      timeline: [],
      searchCenterAddress: "Baillargues",
      searchCenterLat: 43.6606,
      searchCenterLon: 4.0132,
      searchMode: "TRAVEL_TIME",
      searchRadiusKm: 15,
      searchMaxTravelMinutes: 25,
      searchTransportMode: "TRAIN",
      rgpdConsentGranted: true,
      rgpdConsentDate: "2026-11-20",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Invalid transportMode or out-of-range travel minutes fails
  await assertFails(
    clientRef.update({
      rgpdConsentGranted: false,
      rgpdConsentDate: "2026-11-20",
      updatedAt: new Date(Date.now() - 400),
      searchTransportMode: "HELICOPTER",
    })
  );

  // Seed agency & contact for Director Art. 17 erasure
  const agencyId = "ag_rgpd_test";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("agencies").doc(agencyId).set({
      name: "Agence RGPD",
      plan: "AGENCY",
      subscriptionStatus: "ACTIVE",
      ownerUid: ALICE_UID,
      directorUids: [ALICE_UID],
      memberUids: [ALICE_UID, AGENT_A_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("agencies").doc(agencyId).collection("members").doc(ALICE_UID).set({
      agencyId,
      uid: ALICE_UID,
      email: "alice@montyimmo.fr",
      displayName: "Alice",
      role: "DIRECTOR",
      status: "ACTIVE",
      viewerUids: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    });
    await adminDb.collection("agencies").doc(agencyId).collection("members").doc(AGENT_A_UID).set({
      agencyId,
      uid: AGENT_A_UID,
      email: "claire@montyimmo.fr",
      displayName: "Claire",
      role: "AGENT",
      status: "ACTIVE",
      viewerUids: [ALICE_UID, AGENT_A_UID],
      createdAt: now,
      updatedAt: now,
    });
  });

  const ctcRef = aliceDb.collection("agencies").doc(agencyId).collection("contacts").doc("ctc_rgpd");
  await assertSucceeds(
    ctcRef.set({
      agencyId,
      createdByUid: ALICE_UID,
      assignedToUid: AGENT_A_UID,
      assignedToName: "Claire",
      fullName: "Mme Durand",
      contactType: "Acquéreur",
      status: "Qualifié",
      rgpdConsentGranted: true,
      rgpdConsentDate: "2026-11-20",
      rgpdLegalBasis: "Consentement explicite (Art. 6.1.a)",
      rgpdRetentionUntil: "2029-11-20",
      viewerUids: [ALICE_UID, AGENT_A_UID],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Agent cannot delete (Art. 17 requires Director/Admin)
  await assertFails(agentDb.collection("agencies").doc(agencyId).collection("contacts").doc("ctc_rgpd").delete());
  // Director CAN erase (Art. 17)
  await assertSucceeds(ctcRef.delete());
});

test("Seller listing workflow: Particulier cannot self-publish; only staff moderates to PUBLIEE", async () => {
  const sellerDb = testEnv.authenticatedContext(SELLER_UID).firestore();
  const staffDb = testEnv.authenticatedContext(STAFF_UID, { staff: true }).firestore();
  const outsiderDb = testEnv.authenticatedContext(OUTSIDER_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const base = {
    userId: SELLER_UID,
    title: "T3 Lumineux — Les Arceaux",
    sector: "Les Arceaux (34000)",
    address: "Rue Saint-Louis, 34000 Montpellier",
    type: "Appartement",
    price: 345000,
    surface: 72,
    rooms: 3,
    bedrooms: 2,
    dpe: "C",
    publisherType: "PARTICULIER",
    publisherName: "Jean Vendeur",
    hideExactAddress: true,
    ownerConfirmedAccuracy: true,
    legalPublicationConsent: true,
    photoVaultPaths: ["seller_vault/photos/prop_seller_pub_1/photo1.jpg"],
    photoCount: 1,
    saleDelayMinDays: 35,
    saleDelayLikelyDays: 48,
    saleDelayMaxDays: 62,
    saleDelayConfidence: "Moyenne (indicative)",
    saleDelaySummary: "35 à 62 jours (~48 j.)",
    cadastralRef: "34172-000-BX-0142",
    pluZone: "UA1",
    createdAt: now,
    updatedAt: now,
  };
  const propsOf = (db, uid) => db.collection("users").doc(uid).collection("properties");
  const propRef = propsOf(sellerDb, SELLER_UID).doc("prop_seller_pub_1");

  // Création directe en PUBLIEE (explicite ou par défaut) refusée ; usurpation AGENCE refusée sans claim advisor
  await assertFails(propsOf(sellerDb, SELLER_UID).doc("p_direct").set({ ...base, publicationStatus: "PUBLIEE" }));
  await assertFails(propsOf(sellerDb, SELLER_UID).doc("p_default").set({ ...base }));
  await assertFails(propsOf(sellerDb, SELLER_UID).doc("p_spoof_agence").set({ ...base, publisherType: "AGENCE", publicationStatus: "PUBLIEE" }));

  // Création en A_VERIFIER OK
  await assertSucceeds(propRef.set({ ...base, publicationStatus: "A_VERIFIER" }));

  // Le vendeur ne peut ni s'auto-valider ni changer son type d'éditeur
  await assertFails(propRef.update({ publicationStatus: "PUBLIEE", updatedAt: new Date(Date.now() - 800) }));
  await assertFails(propRef.update({ publisherType: "AGENCE", updatedAt: new Date(Date.now() - 800) }));
  await assertFails(propRef.update({ publisherType: "INCONNU", updatedAt: new Date(Date.now() - 800) }));
  await assertFails(propRef.update({ publicationStatus: "AUTRE_STATUT", updatedAt: new Date(Date.now() - 800) }));

  // Un tiers non-staff ne peut rien lire ni modifier
  await assertFails(propsOf(outsiderDb, SELLER_UID).doc("prop_seller_pub_1").get());
  await assertFails(propsOf(outsiderDb, SELLER_UID).doc("prop_seller_pub_1").update({
    publicationStatus: "PUBLIEE",
    updatedAt: new Date(Date.now() - 700),
  }));

  // Le staff peut lire et modérer, mais seulement le statut de publication
  const staffRef = propsOf(staffDb, SELLER_UID).doc("prop_seller_pub_1");
  await assertSucceeds(staffRef.get());
  await assertSucceeds(
    staffRef.update({
      publicationStatus: "PUBLIEE",
      listingStatus: "Annonce Particulier — Vérifiée",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(staffRef.update({ price: 1, updatedAt: new Date(Date.now() - 400) }));
});

test("Agency membership hardening: no arbitrary memberUids, director-only removal, access revoked after removal, directors always visible", async () => {
  const agencyId = "ag_hardening";
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const agency = db.collection("agencies").doc(agencyId);
    await agency.set({
      name: "Agence Hardening",
      plan: "AGENCY",
      subscriptionStatus: "ACTIVE",
      ownerUid: ALICE_UID,
      directorUids: [ALICE_UID],
      memberUids: [ALICE_UID, AGENT_A_UID, AGENT_B_UID],
      createdAt: now,
      updatedAt: now,
    });
    const mk = (uid, name, role, viewers) =>
      agency.collection("members").doc(uid).set({
        agencyId, uid, email: `${uid}@montyimmo.fr`, displayName: name, role, status: "ACTIVE",
        viewerUids: viewers, createdAt: now, updatedAt: now,
      });
    await mk(ALICE_UID, "Alice", "DIRECTOR", [ALICE_UID]);
    await mk(AGENT_A_UID, "Claire", "AGENT", [ALICE_UID, AGENT_A_UID]);
    await mk(AGENT_B_UID, "Marc", "AGENT", [ALICE_UID, AGENT_B_UID]);
    await agency.collection("contacts").doc("ctc_marc").set({
      agencyId, createdByUid: ALICE_UID, assignedToUid: AGENT_B_UID, assignedToName: "Marc",
      fullName: "M. Test", contactType: "Acquéreur", status: "Nouveau",
      viewerUids: [ALICE_UID, AGENT_B_UID], createdAt: now, updatedAt: now,
    });
    await agency.collection("audit_logs").doc("log_1").set({
      agencyId, actorUid: ALICE_UID, actorName: "Alice", actionType: "TEST",
      targetLabel: "x", detail: "x", viewerUids: [ALICE_UID, AGENT_B_UID], createdAt: now,
    });
  });

  const directorDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@montyimmo.fr", email_verified: true }).firestore();
  const agentADb = testEnv.authenticatedContext(AGENT_A_UID, { email: "claire@montyimmo.fr", email_verified: true }).firestore();
  const agentBDb = testEnv.authenticatedContext(AGENT_B_UID, { email: "marc@montyimmo.fr", email_verified: true }).firestore();
  const agency = (db) => db.collection("agencies").doc(agencyId);

  // Un agent actif ne peut pas injecter un UID arbitraire dans memberUids
  await assertFails(
    agency(agentADb).update({
      memberUids: [ALICE_UID, AGENT_A_UID, AGENT_B_UID, OUTSIDER_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Le directeur ne peut pas AJOUTER un membre par ce champ, ni se retirer lui-même
  await assertFails(
    agency(directorDb).update({
      memberUids: [ALICE_UID, AGENT_A_UID, AGENT_B_UID, OUTSIDER_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    agency(directorDb).update({ memberUids: [AGENT_A_UID, AGENT_B_UID], updatedAt: new Date(Date.now() - 1000) })
  );

  // Le directeur ne peut pas se rétrograder
  await assertFails(
    agency(directorDb).collection("members").doc(ALICE_UID).update({
      role: "AGENT",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Avant révocation: Marc lit son contact et les logs
  await assertSucceeds(agency(agentBDb).collection("contacts").doc("ctc_marc").get());
  await assertSucceeds(agency(agentBDb).collection("audit_logs").doc("log_1").get());
  await assertSucceeds(agency(agentBDb).get());

  // Révocation atomique par le directeur: retrait de memberUids + suppression du doc membre
  const revoke = directorDb.batch();
  revoke.update(agency(directorDb), { memberUids: [ALICE_UID, AGENT_A_UID], updatedAt: new Date(Date.now() - 800) });
  revoke.delete(agency(directorDb).collection("members").doc(AGENT_B_UID));
  await assertSucceeds(revoke.commit());

  // Après révocation: plus aucun accès, même si viewerUids contient encore Marc
  await assertFails(agency(agentBDb).collection("contacts").doc("ctc_marc").get());
  await assertFails(agency(agentBDb).collection("audit_logs").doc("log_1").get());
  await assertFails(agency(agentBDb).get());
  await assertFails(
    agency(agentBDb).collection("contacts").where("viewerUids", "array-contains", AGENT_B_UID).limit(100).get()
  );

  // Un agent doit inclure les directeurs dans viewerUids de ses nouveaux contacts
  const baseContact = {
    agencyId, createdByUid: AGENT_A_UID, assignedToUid: AGENT_A_UID, assignedToName: "Claire",
    fullName: "Mme Nouvelle", contactType: "Acquéreur", status: "Nouveau",
    createdAt: new Date(Date.now() - 600), updatedAt: new Date(Date.now() - 600),
  };
  await assertFails(
    agency(agentADb).collection("contacts").doc("ctc_hidden").set({ ...baseContact, viewerUids: [AGENT_A_UID] })
  );
  await assertSucceeds(
    agency(agentADb).collection("contacts").doc("ctc_visible").set({ ...baseContact, viewerUids: [ALICE_UID, AGENT_A_UID] })
  );
});

test("Kotlin PropertyItem.toCreateMap() full 56-field payload and update pass cleanly", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { advisor: true }).firestore();
  const now = new Date(Date.now() - 2000);
  const fullPropRef = aliceDb.collection("users").doc(ALICE_UID).collection("properties").doc("prop_full_kotlin");

  await assertSucceeds(
    fullPropRef.set({
      userId: ALICE_UID,
      title: "Villa Contemporaine — Castelnau-le-Lez",
      sector: "Castelnau-le-Lez (34170)",
      address: "18 Chemin des Mendrous",
      type: "Maison",
      price: 695000,
      surface: 148,
      rooms: 5,
      bedrooms: 4,
      floor: "R+1",
      dpe: "A",
      ges: "A",
      monthlyCharges: 0,
      propertyTax: 2400,
      estimatedWorks: 0,
      listingStatus: "Vérifiée — Publiée",
      source: "vérifiée",
      exterior: "Jardin paysager + Piscine",
      parking: "Garage double + 2 places",
      environment: "Calme résidentiel proche Tram L2",
      transports: "Tram L2 Charles de Gaulle 7 min",
      schoolsShops: "Lycée Pompidou, commerces",
      generalCondition: "Neuf / Récent",
      strengths: ["Piscine chauffée", "DPE A", "Suite parentale RDC"],
      weaknesses: ["Taxe foncière"],
      description: "Belle villa contemporaine lumineuse avec jardin clos et piscine.",
      mandateRef: "MND-2026-300",
      mandateType: "Exclusif",
      mandateDate: "2026-11-18",
      agencyFeePercent: 4.0,
      listingUrl: "https://www.seloger.com/annonces/123.htm",
      listingPortal: "SeLoger",
      dvfEstimatedEuro: 685000,
      dvfEstimatedLowEuro: 655000,
      dvfEstimatedHighEuro: 715000,
      dvfScore: 89,
      dvfCriteriaSummary: "DVF 300m • État neuf • Piscine",
      advisorValuationNotes: "Prix cohérent avec les ventes récentes du secteur.",
      latitude: 43.6352,
      longitude: 3.9012,
      publisherType: "AGENCE",
      publisherName: "Agence Monty",
      publicationStatus: "PUBLIEE",
      hideExactAddress: false,
      ownerConfirmedAccuracy: true,
      legalPublicationConsent: true,
      photoVaultPaths: ["seller_vault/photos/prop_full/p1.jpg"],
      photoCount: 1,
      saleDelayMinDays: 28,
      saleDelayLikelyDays: 42,
      saleDelayMaxDays: 60,
      saleDelayConfidence: "Élevée",
      saleDelaySummary: "28 à 60 jours (~42 j.)",
      cadastralRef: "34057-000-AK-0041",
      pluZone: "UD2",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    fullPropRef.update({
      price: 689000,
      advisorValuationNotes: "Ajustement prix validé vendeur",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

