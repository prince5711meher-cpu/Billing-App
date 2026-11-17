# Meher Basan Bhandar --- Implementation Plan

**Version:** 1.0\
**Goal:** Build and validate the Android and Web billing application in
small, testable milestones.

------------------------------------------------------------------------

## 1. Delivery strategy

Build the application in vertical slices. Each milestone should produce
a working, testable result before the next milestone begins.

Do not ask a coding agent to generate the entire application in one
prompt. First inspect the repository and environment, then implement one
phase at a time.

## 2. Prerequisites

-   Flutter SDK installed and working.
-   Android SDK and a working emulator or Android device.
-   Chrome or another supported browser.
-   Git installed and repository initialized.
-   Supabase account and separate development project.
-   Code editor or OpenCode configured.
-   Target Android phone and printer models identified for later
    testing.

Before coding, run and record: - `flutter doctor -v` -
`flutter --version` - `dart --version` - Android build test. - Flutter
Web build test.

Do not proceed while required toolchain errors remain unresolved.

## 3. Milestones

### Milestone 0 --- Scope and repository baseline

**Tasks** - Confirm V1 exclusions. - Confirm invoice numbering and
financial-year policy. - Confirm GST configuration requirements with the
business owner/accountant. - Create Git repository. - Add the four
project documents. - Create issue list and milestone branches/tags.

**Deliverables** - Clean repository. - Scope checklist. - Development
README.

**Exit criteria** - `flutter doctor` has no blocking issue. - Android
and Web starter builds run.

### Milestone 1 --- Flutter foundation

**Tasks** - Create Flutter project. - Configure Android and Web
targets. - Add routing and Riverpod. - Establish feature-oriented folder
structure. - Add theme tokens and Inter font. - Add responsive app
scaffold. - Add linting and formatting. - Add environment configuration
without committing secrets.

**Deliverables** - App shell. - Android navigation. - Web sidebar. -
Light/dark theme. - Reusable UI components.

**Exit criteria** - App runs on Android and Web. - Layout adapts to
phone, tablet, and desktop. - No feature screen contains hard-coded
production figures.

### Milestone 2 --- Design implementation

**Tasks** - Build Login screen. - Build Dashboard with loading, empty,
error, and offline states. - Build Invoice list. - Build Customer
list/profile. - Build Settings. - Build New Bill form using temporary
local state only. - Validate visual direction against the Invox
reference.

**Deliverables** - Complete navigable UI prototype. - Responsive screen
layouts. - Component catalogue.

**Exit criteria** - All navigation works. - Mobile and desktop layouts
are reviewed. - Form validation and accessibility basics are present.

### Milestone 3 --- Supabase and data foundation

**Tasks** - Create development Supabase project. - Define migrations for
core tables. - Add foreign keys, indexes, and constraints. - Configure
Supabase Auth. - Enable and test RLS. - Add local database layer for
Android and IndexedDB-compatible layer for Web. - Implement repository
interfaces. - Add database migration documentation.

**Deliverables** - Authenticated owner session. - Cloud schema. - Local
persistence. - Repository layer.

**Exit criteria** - Owner can sign in. - Unauthorized access tests fail
safely. - Customer test data persists locally and in cloud.

### Milestone 4 --- Billing domain and invoice engine

**Tasks** - Implement money value type. - Implement quantity and line
calculation. - Implement fixed and percentage discounts. - Implement
GST/non-GST calculation rules. - Implement tax breakdown. - Implement
invoice numbering strategy. - Implement invoice draft and
finalization. - Implement invoice snapshots. - Add calculation unit
tests.

**Deliverables** - Tested invoice calculation engine. - New Bill
workflow. - Saved invoice and invoice detail screen.

**Exit criteria** - Calculation tests pass. - Invoice totals
reconcile. - Duplicate invoice creation is prevented. - Historical
invoice snapshot is stable.

### Milestone 5 --- Customers, payments, and ledger

**Tasks** - Customer create/edit/search. - Customer groups. - Invoice
history. - Payment recording. - Cash, UPI, bank, card, and split payment
support. - Partial payment and credit sales. - Payment allocations. -
Customer ledger and running balance. - Outstanding balance report. -
Audited corrections and reversals.

**Deliverables** - Customer management. - Payment management. - Customer
ledger. - Credit-sales workflow.

**Exit criteria** - Invoice due and customer balance reconcile. -
Allocation limits are enforced. - Payment corrections preserve audit
history.

### Milestone 6 --- Documents and printing

**Tasks** - Build A4 invoice PDF. - Build 58mm thermal layout. - Build
80mm thermal layout. - Build quotation PDF. - Add system share flow for
WhatsApp. - Add browser print support. - Test text wrapping, tax
breakdown, and totals.

**Deliverables** - PDF invoices. - Quotations. - Print and share
actions.

**Exit criteria** - PDF reflects the saved invoice snapshot. - Printer
output is readable on actual supported hardware. - Sharing works without
exposing unintended customer data.

### Milestone 7 --- Cash register and reports

**Tasks** - Cash session opening. - Cash entries and adjustments. -
Expected cash calculation. - Cash count and closing variance. - Daily,
weekly, and monthly sales. - Payment method reports. - GST summary. -
Product-description-wise report. - PDF and Excel exports.

**Deliverables** - Cash register. - Reports and export screens.

**Exit criteria** - Report totals reconcile to source transactions. -
Closed cash sessions cannot be silently modified. - Excluded reports are
not shown as if supported.

### Milestone 8 --- Offline synchronization

**Tasks** - Implement outbox queue. - Add stable local IDs. - Add
idempotent cloud upserts/transactions. - Implement retry and backoff. -
Add sync status UI. - Implement conflict handling. - Test offline
invoice and payment flows. - Test Android-to-Web sync. - Add
backup/export and restore.

**Deliverables** - Offline-first operation. - Sync status and
recovery. - Backup and restore.

**Exit criteria** - Repeated sync does not duplicate financial
records. - Offline records survive restart. - Conflicts are surfaced
safely. - Restore procedure is verified.

### Milestone 9 --- Hardening and release

**Tasks** - Unit, widget, integration, and end-to-end tests. - RLS
security review. - GST calculation review with qualified professional. -
Performance checks. - Android device testing. - Browser compatibility
testing. - Printer compatibility testing. - Production environment
setup. - Signed Android build. - Web deployment over HTTPS. - Backup and
restore rehearsal.

**Deliverables** - Release candidate. - Deployment instructions. - User
guide. - Known limitations list.

**Exit criteria** - No unresolved critical financial/security defects. -
Release checklist completed. - Owner approves real-world pilot.

## 4. Suggested task order for OpenCode

Use this order: 1. Inspect the project and environment. 2. Read PRD,
DRD, TRD, and this plan. 3. Report conflicts or missing decisions. 4.
Create the project skeleton. 5. Build the design system and navigation.
6. Build screens with realistic local sample data. 7. Add database
migrations and RLS. 8. Add repositories and local persistence. 9.
Implement invoice calculation domain. 10. Implement billing UI and
persistence. 11. Implement customers and payments. 12. Implement PDF,
print, and share. 13. Implement reports and cash register. 14. Implement
synchronization. 15. Test, fix, and prepare release.

## 5. OpenCode working rules

-   Read all four documents before making changes.
-   Do not silently add excluded features.
-   Do not invent GST rates or legal rules.
-   Do not hard-code credentials or secrets.
-   Do not store money in floating-point fields.
-   Do not delete financial records permanently.
-   Keep calculation logic outside widgets.
-   Write tests alongside financial features.
-   Run formatter, analyzer, and relevant tests after each milestone.
-   Make small commits with descriptive messages.
-   Never claim a feature is complete without running its relevant
    tests.
-   If a dependency or platform capability is uncertain, investigate it
    and report the limitation before building around it.
-   Keep a `docs/DECISIONS.md` file for confirmed decisions and
    unresolved questions.
-   Update a `docs/PROGRESS.md` file after each milestone.

## 6. Suggested OpenCode master prompt

Read the following files completely: - `docs/PRD.md` - `docs/DRD.md` -
`docs/TRD.md` - `docs/IMPLEMENTATION_PLAN.md`

You are implementing Meher Basan Bhandar, an Android and Web retail
billing application.

First inspect the existing repository, installed Flutter/Dart toolchain,
Android setup, and current Git status. Do not overwrite existing work.

Follow the documents as the source of truth. Preserve the V1 exclusions.
Do not implement inventory, saved product catalogue, barcode features,
supplier/purchase management, expense tracking, staff accounts, or
multiple branches.

Work milestone by milestone. Begin with Milestone 0 and Milestone 1
only. Create the project structure, configure Android and Web, add
routing, Riverpod, design tokens, responsive navigation, and the basic
app shell. Do not start billing/database implementation until the
foundation builds successfully.

Before editing: 1. Summarize the repository and environment. 2. Identify
blockers. 3. List the exact files you plan to create or modify.

After editing: 1. Run `dart format`. 2. Run `flutter analyze`. 3. Run
Android and Web build checks. 4. Fix errors introduced by your changes.
5. Summarize changed files and test results. 6. Update
`docs/PROGRESS.md` and `docs/DECISIONS.md`.

Do not claim tests passed unless they were actually run. Ask for
clarification when a decision affects financial correctness, GST
compliance, data integrity, or invoice numbering.

## 7. Release checklist

### Product

-   [ ] Scope matches PRD.
-   [ ] Excluded features remain excluded.
-   [ ] Owner approves the billing workflow.

### Financial correctness

-   [ ] GST/non-GST totals tested.
-   [ ] Discount calculations tested.
-   [ ] Partial and split payments tested.
-   [ ] Customer ledger reconciles.
-   [ ] Cash closing reconciles.
-   [ ] Invoice numbering verified.

### Security and data

-   [ ] RLS tested.
-   [ ] Secrets excluded from repository.
-   [ ] Offline sync idempotency tested.
-   [ ] Backup and restore tested.
-   [ ] Audit trail verified.

### UI and platform

-   [ ] Android tested on target device.
-   [ ] Web tested in target browser.
-   [ ] Responsive layouts verified.
-   [ ] Light/dark theme verified.
-   [ ] PDF and print output verified.
-   [ ] WhatsApp share flow verified.

### Deployment

-   [ ] Production Supabase project configured.
-   [ ] Database migrations applied.
-   [ ] Web deployed over HTTPS.
-   [ ] Android release signed.
-   [ ] Owner handover completed.
