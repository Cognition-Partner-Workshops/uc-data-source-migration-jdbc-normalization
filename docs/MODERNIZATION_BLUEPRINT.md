# Modernization Blueprint

## Executive Summary

This document evaluates four modernization strategies — **Strangler Fig**, **Replatform**, **Refactor**, and **Rewrite** — for each functional area of the legacy CDW-based Loan Service application. The system currently operates on a Corporate Data Warehouse (CDW) schema with all-VARCHAR typing, cryptic abbreviated column names, denormalized structures, and no referential integrity. The target state is a normalized relational schema with proper data types, foreign key constraints, and clean naming conventions running on Spring Boot 3.2 / Java 17.

---

## System Inventory

| Aspect | Current State |
|--------|---------------|
| **Runtime** | Spring Boot 3.2, Java 17, H2 in-memory |
| **Data Layer** | 4 legacy CDW tables (all VARCHAR, no FKs) |
| **Service Layer** | Single `LoanService` with inline type-conversion logic |
| **API Layer** | 2 REST controllers, 5 endpoints |
| **Entity Count** | 4 legacy JPA entities (`LegacyBorrower`, `LegacyLoanAccount`, `LegacyLoanProduct`, `LegacyPayment`) |
| **Repository Count** | 4 Spring Data repositories |
| **DTO Count** | 3 (`BorrowerDto`, `LoanSummaryDto`, `PaymentDto`) |
| **Test Coverage** | Minimal (single application context test) |
| **Data Volume** | 5 borrowers, 5 products, 5 loan accounts, 10 payments |

---

## Functional Areas

### 1. Borrower Management

**Description:** Manages borrower identity, contact, credit, and employment information. Data sourced from `CDW_BORR_MSTR` (19 VARCHAR columns). Exposed via `/api/borrowers` and `/api/borrowers/{id}`.

**Current Pain Points:**
- All fields stored as VARCHAR including credit score, annual income, and dates
- Date of birth stored as `MM/DD/YYYY` string
- Annual income stored with comma-formatted strings (`"92,500"`)
- Status codes are cryptic abbreviations (`ACT`, `INA`)
- No validation constraints

| Strategy | Fit | Rationale |
|----------|-----|-----------|
| **Strangler Fig** | **Recommended** | Introduce modern `borrowers` entity behind a feature flag. Run dual-read against legacy for validation. Retire legacy entity once parity is confirmed. Low risk because borrower data is read-only in current API and has no transactional coupling to other domains. |
| Replatform | Moderate | Lift-and-shift to modern schema DDL would fix types but miss the opportunity to improve the service layer translation logic. |
| Refactor | Moderate | In-place refactoring of `LegacyBorrower` entity to use proper types would require simultaneous schema migration — risky for a single-step change. |
| Rewrite | Overkill | The borrower surface area is small (1 entity, 1 repository, 2 endpoints). A full rewrite is unnecessary. |

---

### 2. Loan Product Catalog

**Description:** Stores mortgage product definitions (30-Year Fixed, 15-Year Fixed, ARM, FHA, VA). Sourced from `CDW_LN_PROD` (10 VARCHAR columns). Referenced by loan accounts via `PROD_CD`.

**Current Pain Points:**
- Term months, min/max amounts stored as strings
- Active/inactive status encoded as `ACT`/`INA` instead of boolean
- Effective/expiration dates stored as `MM/DD/YYYY` strings
- No min/max amount validation

| Strategy | Fit | Rationale |
|----------|-----|-----------|
| **Strangler Fig** | **Recommended** | Product catalog is a natural reference-data domain with no write path in the current API. Introduce the modern `loan_products` table, populate via ETL, and switch the service layer to read from it. The legacy table can be tombstoned without impacting consumers. |
| Replatform | Good | Simple schema swap is feasible given the small surface area, but loses the incremental validation benefit. |
| Refactor | Low | Limited value — the entity is already simple and the main issue is the schema, not the code structure. |
| Rewrite | Overkill | Only 1 entity, 1 repository, no dedicated controller. |

---

### 3. Loan Account Management

**Description:** Core domain — manages loan origination, balances, interest rates, payment schedules, delinquency, escrow, and property collateral. Sourced from `CDW_LN_ACCT` (25 VARCHAR columns, heavily denormalized with embedded borrower fields). Exposed via `/api/loans` and `/api/loans/{id}`.

**Current Pain Points:**
- **Denormalization:** Borrower first name, last name, and SSN last-4 duplicated in every loan record
- All monetary values (`LN_ORIG_AMT`, `LN_CURR_BAL`, `LN_PMT_AMT`, `LN_ESCROW_BAL`, `PROP_APRS_VAL`) stored as comma-formatted strings
- Interest rate stored as string (`"5.250"`)
- LTV percentage stored as string
- Property type encoded as cryptic codes (`SFR`, `CND`, `MFR`, `TWN`)
- Loan status codes (`ACT`, `CLO`, `DFT`, `FRB`) require runtime expansion
- No foreign key to `CDW_BORR_MSTR` or `CDW_LN_PROD` — only logical references via `BORR_ID` and `PROD_CD`
- Service layer contains heavy inline type-conversion (`parseLegacyAmount`, `parseLegacyDecimal`, `expandStatusCode`, `expandPropertyType`)

| Strategy | Fit | Rationale |
|----------|-----|-----------|
| **Strangler Fig** | **Recommended** | This is the highest-complexity, highest-risk area. Strangler Fig allows incremental migration: (1) create modern `loan_accounts` table with FK constraints, (2) run dual-read mode comparing legacy vs. modern responses, (3) cut over once golden-file regression passes. The denormalization removal (dropping embedded borrower fields, adding `borrower_id` FK) is a structural change that benefits from incremental validation. |
| Replatform | Risky | A direct schema swap for 25 columns with type conversions, FK resolution, and denormalization removal in one step is high-risk. |
| Refactor | Moderate | Could refactor the entity incrementally (e.g., add computed properties) but the fundamental issue is the schema, not the code. |
| Rewrite | Disproportionate | The API contract is stable and the service logic is sound — only the data layer needs replacement. |

---

### 4. Payment Processing

**Description:** Records payment history (principal, interest, escrow, late fees). Sourced from `CDW_PMT_HIST` (14 VARCHAR columns). Exposed via `/api/loans/{loanId}/payments`.

**Current Pain Points:**
- All monetary amounts stored as comma-formatted strings
- Payment types encoded as codes (`REG`, `EXT`, `PRT`, `PRE`)
- Payment statuses encoded as codes (`PST`, `REV`, `NSF`, `PND`)
- Payment dates, received dates, and processed dates stored as `MM/DD/YYYY` strings
- No FK constraint to loan accounts — linked via `LN_ACCT_NBR` string match
- Sequence number used as primary key (string-typed)

| Strategy | Fit | Rationale |
|----------|-----|-----------|
| **Strangler Fig** | **Recommended** | Payment records depend on loan accounts (FK to `loan_accounts.id`). Migrate after loan accounts are stable in the modern schema. Use dual-read validation to confirm amounts parse correctly (critical for financial accuracy). |
| Replatform | Moderate | Feasible as a follow-on to loan account migration, but risky if done in isolation due to FK dependency. |
| Refactor | Low | Same issue as other areas — the schema is the root cause, not the code structure. |
| Rewrite | Overkill | Stable API contract, straightforward read path. |

---

### 5. Data Translation / Anti-Corruption Layer

**Description:** The `LoanService` class contains ~60 lines of inline type-conversion logic (`parseLegacyAmount`, `parseLegacyDecimal`, `parseLegacyInteger`, `expandStatusCode`, `expandPropertyType`, `expandPaymentType`, `expandPaymentStatus`). This is effectively an anti-corruption layer (ACL) translating the legacy CDW representation into clean DTOs.

**Current Pain Points:**
- Translation logic is embedded in the service layer rather than isolated
- No error handling for malformed data (e.g., non-numeric strings in amount fields)
- No logging of data quality issues
- Tight coupling between legacy entity structure and translation logic

| Strategy | Fit | Rationale |
|----------|-----|-----------|
| **Refactor** | **Recommended** | Extract translation logic into a dedicated `LegacyTranslationService` or mapper class. This creates a clean seam for the Strangler Fig migration: once modern entities have proper types, the translation layer simply becomes a pass-through and can be deleted. |
| Strangler Fig | Good | Could be addressed as part of the broader Strangler Fig approach for each domain, but isolating it first makes the later migration cleaner. |
| Replatform | N/A | Not a data-layer concern. |
| Rewrite | Overkill | The logic is correct; it just needs better placement. |

---

## Recommended Strategy Summary

| Functional Area | Recommended Strategy | Complexity | Priority |
|-----------------|---------------------|------------|----------|
| Data Translation Layer | Refactor (extract ACL) | Low | 1 — Do first |
| Loan Product Catalog | Strangler Fig | Low | 2 — Reference data, no deps |
| Borrower Management | Strangler Fig | Low–Medium | 3 — Required before loan accounts |
| Loan Account Management | Strangler Fig | High | 4 — Core domain, depends on borrowers + products |
| Payment Processing | Strangler Fig | Medium | 5 — Depends on loan accounts |

---

## Cross-Cutting Concerns

### Data Integrity Validation
- Implement golden-file regression testing before any migration step
- Capture current API responses as baseline snapshots
- Compare modern-schema responses field-by-field post-migration

### Dual-Read Mode
- Implement a feature flag (`datasource.mode=legacy|modern|dual`) to switch between data sources at runtime
- In dual mode, read from both sources and log discrepancies without affecting API responses

### Type Safety
- Modern entities must use `LocalDate`, `BigDecimal`, `Long`, `Boolean` — never `String` for typed data
- JPA `@ManyToOne` / `@OneToMany` relationships must enforce FK constraints
- CHECK constraints on status and type enum fields

### Backward Compatibility
- REST API contract must remain identical throughout migration (same endpoints, same JSON response shapes)
- DTOs already use proper types (`BigDecimal`, `Integer`) — the translation burden shifts from service layer to JPA/Hibernate

### SSN / PII Handling
- Legacy `BORR_SSN_ENCR` should be re-encrypted during migration (per column mapping guidance)
- Modern `ssn_hash` column should use a stronger hashing algorithm
- Denormalized `BORR_SSN_LST4` in `CDW_LN_ACCT` must be dropped — not migrated to modern schema
