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

test("Unauthenticated user: cannot read invoices", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("invoices").get());
});

test("Authenticated user: cannot read another user's invoices", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("invoices").doc("inv_1").set({
      userId: BOB_UID,
      invoiceNumber: "INV-001",
      documentType: "INVOICE",
      billingMode: "NON_GST",
      subtotalMinor: 50000,
      grandTotalMinor: 50000,
      amountPaidMinor: 50000,
      balanceDueMinor: 0,
      status: "PAID",
      items: [],
      createdAt: new Date(Date.now() - 10000),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("invoices").doc("inv_1").get());
});

test("Authenticated user: can create and edit own invoices (items, item discounts, overall discount)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const invRef = aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_alice_1");
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    invRef.set({
      userId: ALICE_UID,
      invoiceNumber: "BILL-20261117-0001",
      documentType: "INVOICE",
      billingMode: "NON_GST",
      subtotalMinor: 10000,
      discountType: "FIXED",
      discountValue: 0,
      discountMinor: 0,
      grandTotalMinor: 10000,
      amountPaidMinor: 10000,
      balanceDueMinor: 0,
      paymentMethod: "CASH",
      status: "PAID",
      items: [{ description: "Steel Kadai", quantity: 1, rateMinor: 10000, discountType: "FIXED", discountValue: 0, discountMinor: 0, lineTotalMinor: 10000 }],
      createdAt: created,
      updatedAt: created,
    })
  );

  // Edit saved bill items & discounts while keeping userId, invoiceNumber, and createdAt immutable
  await assertSucceeds(
    invRef.update({
      subtotalMinor: 18000,
      discountType: "PERCENTAGE",
      discountValue: 10,
      discountMinor: 1800,
      grandTotalMinor: 16200,
      amountPaidMinor: 16200,
      balanceDueMinor: 0,
      paymentMethod: "UPI",
      status: "PAID",
      items: [{ description: "Steel Kadai", quantity: 2, rateMinor: 10000, discountType: "FIXED", discountValue: 20, discountMinor: 2000, lineTotalMinor: 18000 }],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable invoiceNumber cannot be altered during update
  await assertFails(
    invRef.update({
      invoiceNumber: "TAMPERED-001",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated user: cannot delete invoices (append-only financial integrity)", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("invoices").doc("inv_alice_1").set({
      userId: ALICE_UID,
      invoiceNumber: "INV-001",
      documentType: "INVOICE",
      billingMode: "NON_GST",
      subtotalMinor: 10000,
      grandTotalMinor: 10000,
      amountPaidMinor: 10000,
      balanceDueMinor: 0,
      status: "PAID",
      items: [],
      createdAt: new Date(Date.now() - 5000),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_alice_1").delete()
  );
});

test("Authenticated user: can create, update, and delete own saved items", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const itemRef = aliceDb.collection("users").doc(ALICE_UID).collection("items").doc("item_steel_thali_123abc");
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    itemRef.set({
      userId: ALICE_UID,
      name: "Steel Thali",
      normalizedName: "steel thali",
      unitLabel: "pcs",
      rateMinor: 15000,
      defaultDiscountType: "FIXED",
      defaultDiscountValue: 0,
      hsnSac: "7323",
      taxRateBps: 1200,
      useCount: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    itemRef.update({
      name: "Steel Thali Heavy",
      normalizedName: "steel thali heavy",
      rateMinor: 18000,
      defaultDiscountType: "PERCENTAGE",
      defaultDiscountValue: 5,
      useCount: 2,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    itemRef.update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(itemRef.delete());
});

test("Unauthenticated and cross-user access to saved items is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("items").doc("item_bob_1").set({
      userId: BOB_UID,
      name: "Cooker 3L",
      normalizedName: "cooker 3l",
      unitLabel: "pcs",
      rateMinor: 120000,
      createdAt: new Date(Date.now() - 5000),
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(BOB_UID).collection("items").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("items").doc("item_bob_1").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("items").doc("item_alice_hack").set({
      userId: ALICE_UID,
      name: "Hack Item",
      normalizedName: "hack item",
      unitLabel: "pcs",
      rateMinor: 100,
      createdAt: new Date(Date.now() - 5000),
    })
  );
});

