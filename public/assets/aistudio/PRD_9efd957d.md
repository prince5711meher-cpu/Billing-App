# Meher Basan Bhandar --- Product Requirements Document (PRD)

**Version:** 1.0\
**Status:** Draft for implementation\
**Platforms:** Android and Web\
**Business:** Meher Basan Bhandar\
**Business type:** Retail shop selling utensils, kitchenware, household
goods and plastic items\
**Currency:** Indian Rupee (₹)\
**Primary user:** Shop owner\
**Billing modes:** GST and non-GST

------------------------------------------------------------------------

## 1. Product overview

Meher Basan Bhandar is a retail billing and business-management
application for creating invoices, recording payments, maintaining
customer accounts, tracking credit balances, and reviewing sales.

The product will have Android and web clients backed by a shared cloud
database. It must support practical offline billing and later
synchronization.

The UI should take visual inspiration from the linked Invox
invoice-management Dribbble reference: clean, spacious, minimal,
finance-oriented, and restrained in its use of colour. It must adapt
that visual direction to a physical retail shop rather than copying the
reference.

## 2. Goals

1.  Create a standard bill quickly using manually entered item details.
2.  Support both GST and non-GST invoices.
3.  Record full, partial, split, and credit payments.
4.  Maintain customer purchase history, ledgers, and outstanding
    balances.
5.  Generate, print, download, and share invoices.
6.  Provide daily, weekly, and monthly sales reporting.
7.  Support offline billing with safe synchronization.
8.  Provide cloud backup, data export, and restoration.
9.  Deliver a consistent experience on Android, desktop web, and
    tablet-sized layouts.

## 3. Non-goals for version 1

The following are intentionally excluded from the first release:

-   Product catalogue management.
-   Saved product categories, product images, SKU, or product codes.
-   Barcode generation or scanning.
-   Product variants and saved units.
-   Purchase price and selling price catalogue.
-   Inventory and stock tracking.
-   Supplier management and purchase invoices.
-   Purchase returns and supplier ledgers.
-   Expense tracking and expense reports.
-   Staff accounts and staff permissions.
-   Multiple shops or branches.

Because products and purchase costs are excluded, invoice line items
will be entered manually. Product-wise sales reports can group manually
entered descriptions, but stock valuation and reliable profit
calculations are not available in V1. Profit reporting must remain
disabled or clearly marked unavailable until cost data is introduced.

## 4. Target user and usage context

**Primary user:** The owner/operator of Meher Basan Bhandar.

**Usage context:** - Fast checkout at a physical retail counter. -
Frequent repeat customers. - Cash, UPI, bank transfer, and card
payments. - Some customers may pay partially or on credit. - The owner
may use Android at the shop and web from another device. - Connectivity
may be intermittent.

## 5. Core user journeys

### 5.1 Create and settle a bill

1.  Open **New Bill**.
2.  Select an existing customer or continue as walk-in customer.
3.  Add one or more manually entered line items.
4.  Enter item description, quantity, and rate.
5.  Choose GST or non-GST mode.
6.  Apply an optional discount.
7.  Review subtotal, tax, and grand total.
8.  Record one payment method or split the payment.
9.  Save the invoice.
10. Print, download PDF, or share the invoice.

### 5.2 Record a credit sale

1.  Create an invoice.
2.  Record the amount received, if any.
3.  Leave the remaining amount as due.
4.  Link the due to the customer.
5.  Show the outstanding balance in the customer profile and ledger.

### 5.3 Record a later payment

1.  Open the customer or invoice.
2.  Select **Record Payment**.
3.  Enter amount, date, method, and optional reference.
4.  Allocate the payment to the relevant invoice(s).
5.  Recalculate invoice balance and customer outstanding balance.
6.  Add an immutable payment/audit entry.

### 5.4 Create a quotation

1.  Select or enter a customer.
2.  Add manually entered items.
3.  Set validity date and optional notes.
4.  Save and share/print the quotation.
5.  Convert the quotation into an invoice without duplicating its items.

### 5.5 Close the cash register

1.  Open the current cash session.
2.  Review recorded cash receipts and cash refunds/adjustments.
3.  Enter the physical cash count.
4.  Show expected cash, counted cash, and difference.
5.  Confirm closing and preserve the closing record.

## 6. Functional requirements

### 6.1 Dashboard

-   Show today's sales total and invoice count.
-   Show collections by payment method.
-   Show customer outstanding total.
-   Show recent invoices and their statuses.
-   Show a weekly/monthly sales chart.
-   Provide shortcuts for New Bill, Customers, Invoices, and Reports.
-   Use real database values only; never ship illustrative figures as
    live data.

### 6.2 Billing and invoices

-   Create invoices with multiple manually entered items.
-   Each line item includes description, quantity, unit label (free
    text), rate, discount if supported, tax rate, and calculated amount.
-   Support GST and non-GST modes.
-   Support percentage and fixed invoice discounts.
-   Generate unique invoice numbers.
-   Support quotations/estimates.
-   Generate PDF invoices.
-   Print A4 invoices and 58mm/80mm thermal receipts where supported.
-   Share invoice PDF or a secure shareable representation through
    WhatsApp's system share flow.
-   Permit editing/cancellation only under defined status and audit
    rules.
-   Preserve a snapshot of business and customer details on the invoice
    so historical documents do not change when profiles are edited.

### 6.3 Customers

-   Create and edit customer name, phone, address, GSTIN, and notes.
-   Support walk-in customers without forcing a saved profile.
-   View customer invoice history.
-   View customer ledger with debits, credits, payments, and running
    balance.
-   Support customer groups.
-   Show outstanding amount and overdue status.
-   Generate a WhatsApp payment reminder draft; user confirms before
    sending.

### 6.4 Payments

-   Support cash, UPI, bank transfer, and card.
-   Support split payments for a single invoice.
-   Support partial payments and credit balances.
-   Record payment date, amount, method, reference, and notes.
-   Allocate payments to invoices.
-   Prevent allocations exceeding the available payment or invoice due.
-   Maintain payment history.
-   Support reversals/corrections through auditable adjustment entries,
    not destructive deletion.

### 6.5 GST

-   Support GST and non-GST invoice modes.
-   Capture seller GSTIN and customer GSTIN where applicable.
-   Capture HSN/SAC as a manually entered invoice-line field.
-   Support configurable tax rates.
-   Calculate CGST + SGST for applicable intra-state supplies and IGST
    for applicable inter-state supplies.
-   Capture place-of-supply/state information required for the
    applicable calculation.
-   Generate GST sales summaries.
-   Validate invoice wording, fields, tax treatment, and current legal
    requirements with a qualified tax professional before production
    use.
-   Do not assume a tax rate based only on an item description.

### 6.6 Reports

-   Daily, weekly, and monthly sales.
-   Invoice count and average invoice value.
-   Collections by payment method.
-   Outstanding customer balances.
-   Product-description-wise sales based on invoice lines.
-   Best-selling descriptions based on quantity/revenue, with clear
    grouping limitations.
-   GST summary.
-   Export selected reports to PDF and Excel.
-   Date range and status filters.

**Deferred reports:** Purchase reports, stock valuation, expense
reports, profit after expenses, and accurate gross profit are excluded
because their required purchase, inventory, and expense data are
excluded.

### 6.7 Cash register

-   Open and close a cash session.
-   Record opening cash.
-   Summarize cash sales and cash payments received.
-   Record cash refunds/adjustments with reasons.
-   Calculate expected closing cash.
-   Enter counted cash.
-   Show and preserve cash variance.
-   Prevent edits to a closed session except through an audited
    correction process.

### 6.8 Settings

-   Business name, logo, address, phone, email, and GSTIN.
-   Invoice prefix and numbering settings.
-   Invoice templates and print preferences.
-   Light and dark themes.
-   Owner authentication and app PIN lock.
-   Cloud backup and restore.
-   Export business data.
-   Sync status and last successful sync.

## 7. Platform requirements

### Android

-   Touch-first layout.
-   Quick billing form.
-   Camera permission only if a future feature requires it; barcode
    scanning is excluded.
-   PDF generation and system sharing.
-   Printer support subject to device/printer compatibility.
-   Local data persistence for offline use.

### Web

-   Responsive desktop-first layout with sidebar.
-   Keyboard-friendly billing form.
-   PDF download and browser printing.
-   Responsive tablet layout.
-   Local persistence for offline use where supported by the chosen
    Flutter web storage implementation.

## 8. Design requirements

-   Reference direction: Invox Dribbble invoice-management design.
-   Minimal, premium, finance-oriented interface.
-   Suggested primary accent: electric blue `#0625FA`.
-   Suggested light background: `#F5F5F2`.
-   Main text: `#0A0505`.
-   White surfaces with subtle borders.
-   Muted green for paid/success states.
-   Warm orange for pending/due states.
-   Inter typography and Lucide-style icon language.
-   Android: bottom navigation.
-   Web: collapsible left sidebar.
-   Avoid excessive gradients, shadows, decorative animations, and dense
    tables.
-   Billing speed and readability take priority over decoration.
-   Provide equivalent dark theme with accessible contrast.

## 9. Success metrics and acceptance targets

-   A regular invoice can be entered and saved in under 60 seconds after
    basic familiarity.
-   All invoice totals reconcile to line items, discounts, and tax.
-   Payment allocations never create negative due balances.
-   Invoice IDs and invoice numbers are unique.
-   Offline-created records synchronize without duplication.
-   Customer ledger balances reconcile with invoices and payment
    allocations.
-   PDF and print output matches the saved invoice snapshot.
-   No production dashboard displays fabricated sample data.
-   All critical financial actions are auditable.

## 10. Assumptions and open decisions

-   Initial deployment is for one shop and one owner account.
-   The app currency is INR.
-   GST rates are configurable and must be verified before production.
-   Invoice numbering policy and financial-year reset policy must be
    confirmed.
-   Whether quotations require an expiry period is configurable.
-   WhatsApp sharing uses the operating system share sheet or an
    approved integration; automatic bulk messaging is not assumed.
-   Web offline storage and printer capabilities must be validated early
    with target browsers and devices.
