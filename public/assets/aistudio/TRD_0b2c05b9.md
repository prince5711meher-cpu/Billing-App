# Meher Basan Bhandar --- Technical Requirements Document (TRD)

**Version:** 1.0\
**Platforms:** Android and Web\
**Recommended client:** Flutter\
**Backend:** Supabase / PostgreSQL\
**Architecture:** Offline-first, single-shop, owner-operated V1

------------------------------------------------------------------------

## 1. Technical overview

Build a shared Flutter application for Android and Web, backed by
Supabase. The application must persist supported operations locally and
synchronize them with the cloud when connectivity is available.

Use a modular architecture. Keep UI, application logic, domain rules,
data access, and platform integrations separate.

## 2. Technology stack

  -----------------------------------------------------------------------
  Layer                               Technology
  ----------------------------------- -----------------------------------
  Client                              Flutter / Dart

  State management                    Riverpod

  Routing                             go_router

  Cloud database                      Supabase PostgreSQL

  Authentication                      Supabase Auth

  Authorization                       PostgreSQL Row Level Security

  Android local database              SQLite, using a maintained Flutter
                                      package

  Web local persistence               IndexedDB through a compatible
                                      persistence layer

  PDF                                 Flutter PDF package

  Printing                            Printing package, validated per
                                      platform

  Sharing                             Flutter system share integration

  Charts                              A maintained Flutter chart package

  Serialization                       Generated model serialization where
                                      useful

  Testing                             flutter_test, integration_test

  Source control                      Git + GitHub

  Web deployment                      Static hosting with correct Flutter
                                      routing and HTTPS
  -----------------------------------------------------------------------

Confirm package maintenance, licensing, browser support, and Android
compatibility before adding dependencies.

## 3. Architecture

Use a layered, feature-oriented architecture:

-   Presentation: screens, widgets, form state.
-   Application: use cases and orchestration.
-   Domain: entities, value objects, calculation rules, repository
    contracts.
-   Data: local/cloud data sources and repository implementations.
-   Platform: printer, share, file, and secure-storage adapters.

Suggested flow:

`UI → Riverpod Controller → Use Case → Repository → Local Database / Supabase`

The UI must not calculate invoice totals independently. A shared domain
calculation service must be the single source of truth.

## 4. Suggested project structure

``` text
meher_basan_bhandar/
├── android/
├── web/
├── assets/
│   ├── fonts/
│   ├── icons/
│   └── images/
├── lib/
│   ├── app/
│   │   ├── app.dart
│   │   ├── router.dart
│   │   └── theme/
│   ├── core/
│   │   ├── errors/
│   │   ├── formatting/
│   │   ├── network/
│   │   ├── security/
│   │   ├── sync/
│   │   ├── utils/
│   │   └── widgets/
│   ├── features/
│   │   ├── auth/
│   │   ├── dashboard/
│   │   ├── billing/
│   │   ├── invoices/
│   │   ├── quotations/
│   │   ├── customers/
│   │   ├── payments/
│   │   ├── ledger/
│   │   ├── cash_register/
│   │   ├── reports/
│   │   └── settings/
│   ├── data/
│   │   ├── local/
│   │   ├── remote/
│   │   └── repositories/
│   └── main.dart
├── test/
│   ├── unit/
│   ├── widget/
│   └── integration/
├── docs/
│   ├── PRD.md
│   ├── DRD.md
│   ├── TRD.md
│   └── IMPLEMENTATION_PLAN.md
├── pubspec.yaml
└── README.md
```

## 5. Database requirements

Use UUID primary keys for internal IDs. Use `numeric` or integer paise
for monetary values; never use floating-point types for persisted money.

### 5.1 Core tables

#### profiles

-   id (UUID, references authenticated user)
-   display_name
-   created_at
-   updated_at

#### business_settings

-   id
-   business_name
-   logo_path
-   address
-   phone
-   email
-   gstin
-   state_code
-   currency_code
-   invoice_prefix
-   created_at
-   updated_at

#### customers

-   id
-   name
-   phone
-   address
-   gstin
-   group_id
-   notes
-   created_at
-   updated_at
-   archived_at

#### customer_groups

-   id
-   name
-   created_at

#### invoices

-   id
-   invoice_number
-   document_type (invoice / quotation)
-   customer_id (nullable for walk-in)
-   customer_snapshot (JSON)
-   business_snapshot (JSON)
-   invoice_date
-   due_date (nullable)
-   status
-   billing_mode (gst / non_gst)
-   place_of_supply
-   subtotal_minor
-   discount_type
-   discount_value
-   discount_minor
-   taxable_total_minor
-   cgst_minor
-   sgst_minor
-   igst_minor
-   grand_total_minor
-   amount_paid_minor
-   balance_due_minor
-   notes
-   created_at
-   updated_at
-   cancelled_at
-   cancellation_reason
-   local_id
-   sync_version

#### invoice_items

-   id
-   invoice_id
-   description
-   quantity_decimal
-   unit_label
-   rate_minor
-   discount_minor
-   hsn_sac
-   tax_rate_basis_points
-   taxable_amount_minor
-   cgst_minor
-   sgst_minor
-   igst_minor
-   line_total_minor

#### payments

-   id
-   customer_id (nullable)
-   payment_date
-   method (cash / upi / bank / card / other)
-   amount_minor
-   reference
-   notes
-   status
-   created_at
-   local_id

#### payment_allocations

-   id
-   payment_id
-   invoice_id
-   amount_minor
-   created_at

#### cash_sessions

-   id
-   opened_at
-   opening_cash_minor
-   closed_at
-   expected_cash_minor
-   counted_cash_minor
-   variance_minor
-   status
-   closing_notes

#### cash_entries

-   id
-   cash_session_id
-   entry_type
-   amount_minor
-   reason
-   reference_id
-   created_at

#### invoice_sequences

-   id
-   financial_year
-   prefix
-   next_number
-   updated_at

#### audit_logs

-   id
-   actor_id
-   entity_type
-   entity_id
-   action
-   before_snapshot
-   after_snapshot
-   reason
-   created_at

#### sync_queue

-   id
-   entity_type
-   entity_local_id
-   operation
-   payload
-   created_at
-   retry_count
-   last_error
-   status

### 5.2 Database constraints

-   Unique invoice number within the applicable business/financial-year
    scope.
-   Foreign keys for invoice items, allocations, and related records.
-   Positive quantities and non-negative rates where applicable.
-   Payment allocation cannot exceed payment amount.
-   Total allocations cannot exceed invoice outstanding balance.
-   Cancelled invoices cannot accept new allocations.
-   Use transactions for invoice finalization and payment allocation.
-   Financial records must be corrected by auditable
    reversal/adjustment, not hard deletion.
-   Store invoice snapshots so old documents remain stable after
    business/customer profile edits.

## 6. Monetary and tax calculations

-   Use integer paise or fixed-precision decimal arithmetic.
-   Define and test one rounding policy.
-   Calculate line amounts and invoice totals in the domain layer.
-   Store the applied tax rate and computed tax amounts on each invoice
    line.
-   Do not recalculate historical invoices using newly configured rates.
-   GST mode must require the necessary tax and place-of-supply
    information.
-   CGST/SGST versus IGST selection must follow validated
    place-of-supply rules.
-   Non-GST mode must not accidentally add GST.
-   Discount allocation across taxable lines must be deterministic and
    documented.
-   Tax configuration must be editable only by the owner and changes
    must be audited.
-   Obtain professional validation of GST rules, invoice format, and
    applicable compliance obligations before production.

## 7. Invoice numbering

-   Generate a UUID/local transaction ID immediately for every
    offline-created invoice.
-   Maintain a readable invoice number separately.
-   Do not rely on timestamps alone for uniqueness.
-   Define whether numbers are reserved locally or assigned by a server
    sequence.
-   For a single shop with one active device at a time, a local sequence
    can be used with collision detection during sync.
-   If multiple devices can create invoices offline concurrently, use
    device-specific number segments or another collision-safe numbering
    strategy.
-   Never silently renumber an invoice already issued to a customer.
-   Confirm financial-year reset and numbering policy with the owner.

## 8. Offline-first synchronization

### 8.1 Required behavior

-   Save supported records locally before reporting success.
-   Assign stable UUIDs to local records.
-   Add changes to an outbox/sync queue.
-   Retry failed sync with bounded exponential backoff.
-   Make server operations idempotent using stable IDs and unique
    constraints.
-   Show pending, synced, and failed states.
-   Allow manual retry.
-   Keep a last successful sync timestamp.

### 8.2 Conflict strategy

-   Financial transactions are append-oriented.
-   Do not use blind last-write-wins for invoices or payments.
-   If two edits conflict, preserve both versions or require a
    controlled resolution.
-   Payment and invoice finalization should use server-side database
    transactions.
-   A sync retry must never create duplicate invoices or payments.
-   Offline cancellation/edit rules must be explicit and audited.

### 8.3 Platform storage

-   Android: use a local relational database.
-   Web: use IndexedDB through a supported Flutter implementation.
-   Verify browser persistence and quota behavior.
-   Do not claim offline support for a browser/device combination until
    integration-tested.

## 9. Security

-   Enable Row Level Security on every user-owned table.
-   Scope records to the authenticated owner/business.
-   Never embed Supabase service-role keys in the client.
-   Keep secrets in environment configuration for
    development/deployment, and only expose publishable client
    configuration.
-   Validate all sensitive financial operations on the server/database.
-   Use HTTPS.
-   Protect local app PIN material using platform-appropriate secure
    storage where available.
-   Rate-limit authentication where supported.
-   Avoid logging full customer contact details or sensitive invoice
    data.
-   Provide secure backup and export handling.
-   Test RLS with unauthorized user scenarios.

## 10. Printing, PDF, and WhatsApp sharing

-   Create one invoice document model and render it into A4 and thermal
    layouts.
-   Verify 58mm and 80mm paper widths, margins, line wrapping, and text
    readability.
-   Printer support must be tested on target Android devices and printer
    models.
-   Web printing depends on browser and operating-system print support.
-   PDF generation must work offline for saved invoices.
-   WhatsApp sharing should use a system share sheet or approved share
    mechanism.
-   Do not expose a public invoice URL without access controls and an
    explicit product decision.

## 11. Authentication and account model

-   V1 has one owner account.
-   Use Supabase Auth with email/password unless the owner chooses
    another method.
-   App PIN is an additional local access lock, not a replacement for
    secure authentication.
-   Architecture may support staff later, but staff account screens and
    permissions are out of V1 scope.

## 12. Testing strategy

### Unit tests

-   Subtotal and line totals.
-   Fixed and percentage discounts.
-   GST/non-GST calculations.
-   CGST/SGST/IGST selection.
-   Rounding.
-   Payment allocation.
-   Outstanding balance.
-   Invoice numbering.
-   Cash expected balance.

### Integration tests

-   Create invoice and save locally.
-   Sync invoice to Supabase.
-   Retry sync without duplicates.
-   Record partial payment.
-   Allocate split payments.
-   Cancel invoice with audit log.
-   Generate PDF from saved snapshot.
-   Restore exported data.

### Widget tests

-   New Bill validation.
-   Customer selection.
-   Payment selector.
-   Invoice status.
-   Responsive navigation.
-   Light/dark theme.

### End-to-end tests

-   Android online/offline flow.
-   Web online/offline flow.
-   Android-to-web synchronization.
-   Printer compatibility on actual devices.
-   Browser refresh and local persistence.

## 13. Deployment and operations

-   Use separate development and production Supabase projects.
-   Apply database migrations through version control.
-   Keep environment secrets out of Git.
-   Deploy Web over HTTPS.
-   Build signed Android release artifacts.
-   Back up production data regularly.
-   Test restore procedures, not just backup creation.
-   Monitor sync failures and application errors without exposing
    customer data.

## 14. Technical acceptance criteria

-   No financial calculations use binary floating point.
-   RLS prevents cross-account access.
-   Invoice/payment sync is idempotent.
-   Local data survives app/browser restart within supported storage
    constraints.
-   Financial records are auditable.
-   Android and Web use the same business rules.
-   All critical calculation and synchronization tests pass.
