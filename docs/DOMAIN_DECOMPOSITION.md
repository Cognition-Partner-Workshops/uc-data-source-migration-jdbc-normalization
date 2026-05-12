# Domain Decomposition

## Overview

This document identifies the bounded contexts within the legacy CDW-based Loan Service, maps their inter-domain dependencies, and analyzes extraction seams for incremental modernization.

---

## Bounded Contexts

### BC-1: Borrower Identity

**Responsibility:** Owns borrower personal information, contact details, credit profile, and employment status.

| Attribute | Value |
|-----------|-------|
| **Legacy Table** | `CDW_BORR_MSTR` (19 columns) |
| **Modern Table** | `borrowers` (20 columns) |
| **Entity** | `LegacyBorrower` |
| **Repository** | `LegacyBorrowerRepository` |
| **API Endpoints** | `GET /api/borrowers`, `GET /api/borrowers/{id}` |
| **Controller** | `BorrowerController` |
| **Data Sensitivity** | HIGH — contains PII (SSN, DOB, address, phone, email) |

**Core Aggregates:**
- Borrower (root) — identity, contact, credit, employment

**Key Domain Rules:**
- `BORR_ID` is the natural key (format: `B-NNNNN`)
- Status codes: `ACT` (Active), `INA` (Inactive)
- Credit score is an integer (stored as VARCHAR in legacy)
- Annual income is a decimal (stored as comma-formatted string)
- Record type (`BORR_REC_TYP`) is a legacy artifact — dropped in modern schema

---

### BC-2: Product Catalog

**Responsibility:** Defines available mortgage products, their terms, rate types, and eligibility constraints.

| Attribute | Value |
|-----------|-------|
| **Legacy Table** | `CDW_LN_PROD` (10 columns) |
| **Modern Table** | `loan_products` (11 columns) |
| **Entity** | `LegacyLoanProduct` |
| **Repository** | `LegacyLoanProductRepository` |
| **API Endpoints** | None (consumed internally by `LoanService`) |
| **Data Sensitivity** | LOW — no PII |

**Core Aggregates:**
- LoanProduct (root) — product code, type, term, rates, amounts

**Key Domain Rules:**
- `PROD_CD` is the natural key (e.g., `FXD30`, `ARM51`, `FHA30`, `VA30`)
- Product types: `FXD` (Fixed), `ARM` (Adjustable), `FHA`, `VA`
- Rate types: `FIXED`, `VARIABLE`
- Min/max amount constraints define eligibility
- Active/inactive status governs availability

**Current Products in System:**
| Code | Description | Type | Term |
|------|------------|------|------|
| FXD30 | 30-Year Fixed Rate Mortgage | FXD | 360 mo |
| FXD15 | 15-Year Fixed Rate Mortgage | FXD | 180 mo |
| ARM51 | 5/1 Adjustable Rate Mortgage | ARM | 360 mo |
| FHA30 | FHA 30-Year Fixed | FHA | 360 mo |
| VA30 | VA 30-Year Fixed | VA | 360 mo |

---

### BC-3: Loan Origination & Servicing

**Responsibility:** Core business domain — manages the lifecycle of loan accounts from origination through maturity, including balances, rates, delinquency tracking, escrow management, and property collateral.

| Attribute | Value |
|-----------|-------|
| **Legacy Table** | `CDW_LN_ACCT` (25 columns) |
| **Modern Table** | `loan_accounts` (22 columns + 2 FKs) |
| **Entity** | `LegacyLoanAccount` |
| **Repository** | `LegacyLoanAccountRepository` |
| **API Endpoints** | `GET /api/loans`, `GET /api/loans/{id}` |
| **Controller** | `LoanController` |
| **Data Sensitivity** | HIGH — linked to PII via borrower, contains financial data |

**Core Aggregates:**
- LoanAccount (root) — account number, amounts, rates, dates, status
- Property (value object) — address, type, appraised value

**Key Domain Rules:**
- `LN_ACCT_NBR` is the natural key (format: `LN-YYYY-NNNNN`)
- Loan statuses: `ACT` (Active), `CLO` (Closed), `DFT` (Default), `FRB` (Forbearance)
- Delinquency tracked in days
- LTV percent = loan balance / appraised value
- Escrow balance tracked separately
- Property types: `SFR` (Single Family), `CND` (Condominium), `MFR` (Multi-Family), `TWN` (Townhouse)

**Denormalization Issue:**
The legacy table embeds borrower fields (`BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4`) directly in the loan record. The modern schema eliminates this by using a `borrower_id` FK to the `borrowers` table.

---

### BC-4: Payment Ledger

**Responsibility:** Records and tracks all payment transactions against loan accounts, including principal/interest/escrow splits, late fees, and processing status.

| Attribute | Value |
|-----------|-------|
| **Legacy Table** | `CDW_PMT_HIST` (14 columns) |
| **Modern Table** | `payments` (14 columns + FK) |
| **Entity** | `LegacyPayment` |
| **Repository** | `LegacyPaymentRepository` |
| **API Endpoints** | `GET /api/loans/{loanId}/payments` |
| **Controller** | `LoanController` (shared) |
| **Data Sensitivity** | MEDIUM — financial transaction records |

**Core Aggregates:**
- Payment (root) — date, amounts, type, status

**Key Domain Rules:**
- Payment types: `REG` (Regular), `EXT` (Extra), `PRT` (Partial), `PRE` (Prepayment)
- Payment statuses: `PST` (Posted), `REV` (Reversed), `NSF` (Non-Sufficient Funds), `PND` (Pending)
- Each payment splits into principal, interest, escrow, and late fee components
- `PMT_SEQ_NBR` is the legacy sequence key (format: `PMT-YYYYMMNNNNN`)

---

## Dependency Map

```
                    ┌──────────────────────┐
                    │  BC-2: Product       │
                    │  Catalog             │
                    │  (CDW_LN_PROD)       │
                    └──────────┬───────────┘
                               │
                    references │ PROD_CD
                               │
┌──────────────────┐           ▼                ┌──────────────────┐
│  BC-1: Borrower  │◄──────── BC-3: Loan       │  BC-4: Payment   │
│  Identity        │ BORR_ID  Origination &    │  Ledger          │
│  (CDW_BORR_MSTR) │──────►   Servicing        │  (CDW_PMT_HIST)  │
└──────────────────┘          (CDW_LN_ACCT)     └──────────────────┘
                               │                         ▲
                               │   LN_ACCT_NBR           │
                               └─────────────────────────┘
```

**Dependency Direction:**
1. **BC-3 → BC-1:** Loan accounts reference borrowers via `BORR_ID` (plus denormalized copies of borrower fields)
2. **BC-3 → BC-2:** Loan accounts reference products via `PROD_CD`
3. **BC-4 → BC-3:** Payments reference loan accounts via `LN_ACCT_NBR`
4. **BC-1 → none:** Borrowers are independent (no outbound FKs)
5. **BC-2 → none:** Products are independent (no outbound FKs)

---

## Extraction Seam Analysis

### Seam 1: Product Catalog (BC-2) — Cleanest Extraction

**Seam Type:** Data seam + service seam

**Why it's clean:**
- Zero inbound dependencies from other bounded contexts at the data layer (products are only referenced by loan accounts via `PROD_CD`)
- No dedicated API surface — consumed only internally by `LoanService.getAllLoans()` and `LoanService.getLoanById()`
- Small surface area (10 columns, 5 records)
- No PII

**Extraction Steps:**
1. Create modern `loan_products` table with proper types
2. Write ETL to migrate 5 product records (string→typed conversion)
3. Create `ModernLoanProduct` JPA entity with `Long id`, `Integer termMonths`, `BigDecimal minAmount/maxAmount`, `Boolean isActive`, `LocalDate effectiveDate/expirationDate`
4. Create `ModernLoanProductRepository`
5. Update `LoanService` to read from modern repository, matching on `code` instead of `PROD_CD`
6. Validate: product descriptions appear correctly in loan summary API responses

**Coupling to Break:**
- `LoanService` currently builds a `Map<String, LegacyLoanProduct>` keyed by `PROD_CD`. Modern version keys by `code` (same value, different entity type).

**Risk:** Very Low

---

### Seam 2: Borrower Identity (BC-1) — Clean Extraction

**Seam Type:** Data seam + API seam

**Why it's clean:**
- Independent entity — no outbound foreign keys
- Clean API surface (`/api/borrowers`, `/api/borrowers/{id}`)
- Moderate surface area (19 columns, 5 records)

**Extraction Steps:**
1. Create modern `borrowers` table with proper types
2. Write ETL: parse dates (`MM/DD/YYYY → LocalDate`), integers (credit score), decimals (annual income), expand status codes
3. Create `ModernBorrower` JPA entity
4. Create `ModernBorrowerRepository`
5. Update `BorrowerController`/`LoanService` to use modern repository
6. Preserve `external_id` mapping (`BORR_ID` → `external_id`) for cross-referencing

**Coupling to Break:**
- `LegacyLoanAccount` embeds borrower fields (`BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4`). These denormalized copies must NOT be migrated — the modern `loan_accounts` table uses a FK to `borrowers.id` instead.
- `LoanService.toLoanSummary()` reads `acct.getBorrowerFirstName()` and `acct.getBorrowerLastName()` from the loan record. After migration, this must resolve through the borrower FK relationship.

**Prerequisite for:** BC-3 (Loan Origination) requires borrowers to exist in modern schema for FK resolution.

**Risk:** Low

---

### Seam 3: Loan Origination & Servicing (BC-3) — Complex Extraction

**Seam Type:** Data seam + API seam + structural seam (denormalization removal)

**Why it's complex:**
- Largest entity (25 columns)
- Denormalized borrower data must be dropped and replaced with FK
- Two FK dependencies: `borrower_id → borrowers.id`, `product_id → loan_products.id`
- FK resolution requires lookup by natural key (`BORR_ID → external_id → borrowers.id`, `PROD_CD → code → loan_products.id`)
- Property data is embedded (potential future extraction to its own table)
- Multiple type conversions: 7+ amount fields, 5+ date fields, status codes, property type codes

**Extraction Steps:**
1. **Prerequisites:** BC-1 (borrowers) and BC-2 (loan_products) must be migrated first
2. Create modern `loan_accounts` table with FKs and proper types
3. Write ETL:
   - Resolve `BORR_ID` → `borrowers.id` via `external_id` lookup
   - Resolve `PROD_CD` → `loan_products.id` via `code` lookup
   - Parse all amount fields (remove commas, convert to `BigDecimal`)
   - Parse all date fields (`MM/DD/YYYY → LocalDate`)
   - Expand status codes (`ACT→ACTIVE`, `CLO→CLOSED`, `DFT→DEFAULT`, `FRB→FORBEARANCE`)
   - Expand property type codes (`SFR→Single Family`, etc.)
   - Drop denormalized borrower fields
4. Create `ModernLoanAccount` JPA entity with `@ManyToOne` relationships
5. Create `ModernLoanAccountRepository`
6. Update `LoanService`: remove `parseLegacyAmount`, `expandStatusCode`, etc. — modern entities already have proper types
7. Update `toLoanSummary()`: resolve borrower name via FK relationship instead of denormalized fields

**Coupling to Break:**
- Denormalized borrower data in loan records
- String-based logical references → proper FK relationships
- All inline type-conversion logic in `LoanService`

**Risk:** High — largest surface area, most transformations, FK dependency chain

---

### Seam 4: Payment Ledger (BC-4) — Moderate Extraction

**Seam Type:** Data seam + API seam

**Why it's moderate:**
- Single FK dependency on loan accounts (`LN_ACCT_NBR → loan_accounts.id`)
- 14 columns with multiple amount and date fields
- FK resolution requires lookup by natural key (`LN_ACCT_NBR → account_number → loan_accounts.id`)
- Payment sequence number changes from string to auto-increment BIGINT

**Extraction Steps:**
1. **Prerequisite:** BC-3 (loan accounts) must be migrated first
2. Create modern `payments` table with FK and proper types
3. Write ETL:
   - Resolve `LN_ACCT_NBR` → `loan_accounts.id` via `account_number` lookup
   - Parse all amount fields
   - Parse all date fields
   - Expand payment type codes (`REG→REGULAR`, `EXT→EXTRA`, `PRT→PARTIAL`, `PRE→PREPAYMENT`)
   - Expand payment status codes (`PST→POSTED`, `REV→REVERSED`, `NSF→NSF`, `PND→PENDING`)
   - Generate new auto-increment IDs (legacy `PMT_SEQ_NBR` can be preserved as metadata if needed)
4. Create `ModernPayment` JPA entity with `@ManyToOne` to `ModernLoanAccount`
5. Create `ModernPaymentRepository`
6. Update `LoanService.getPaymentsByLoan()` to query by `loan_account_id` instead of `LN_ACCT_NBR` string match

**Coupling to Break:**
- String-based loan account reference → FK relationship
- String-typed sequence ID → auto-increment BIGINT

**Risk:** Medium — financial accuracy is critical; amounts must parse correctly

---

## Extraction Order (Dependency-Driven)

```
Phase 1: BC-2 Product Catalog    ──► No dependencies
Phase 2: BC-1 Borrower Identity  ──► No dependencies
Phase 3: BC-3 Loan Origination   ──► Depends on BC-1 + BC-2
Phase 4: BC-4 Payment Ledger     ──► Depends on BC-3
```

This order respects the FK dependency graph: leaf nodes first (products, borrowers), then the core aggregate (loans), then the transactional records (payments).

---

## Shared Kernel Identification

### Anti-Corruption Layer (ACL)

The `LoanService` class currently serves as an implicit ACL, translating between legacy CDW representations and clean API DTOs. The following translation functions form the shared kernel:

| Function | Purpose | Used By |
|----------|---------|---------|
| `parseLegacyAmount(String)` | Remove commas, parse to `BigDecimal` | Loan summaries, payments |
| `parseLegacyDecimal(String)` | Parse string to `BigDecimal` | Interest rates |
| `parseLegacyInteger(String)` | Parse string to `Integer` | Credit scores |
| `expandStatusCode(String)` | `ACT→Active`, `CLO→Closed`, etc. | Loan accounts |
| `expandPropertyType(String)` | `SFR→Single Family`, etc. | Loan accounts |
| `expandPaymentType(String)` | `REG→Regular`, etc. | Payments |
| `expandPaymentStatus(String)` | `PST→Posted`, etc. | Payments |

**Recommendation:** Extract these into a `LegacyDataTranslator` utility class before starting domain migrations. This isolates the legacy concern and makes it deletable once all domains are migrated.

---

## Future Decomposition Opportunities

### Property as a Separate Bounded Context
The modern `loan_accounts` table still embeds property fields (address, city, state, zip, type, appraised value). A future iteration could extract this into a `properties` table with its own lifecycle (appraisal updates, address corrections).

### Borrower Communication Preferences
The `borrowers` table includes contact fields (phone, email) that could evolve into a `communication_preferences` aggregate if notification features are added.

### Payment Scheduling
The current system only records historical payments. A future `payment_schedule` bounded context could manage upcoming payment due dates, auto-pay, and reminders.
