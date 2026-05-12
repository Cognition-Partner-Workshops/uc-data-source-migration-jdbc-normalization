# Cutover Plan

## Overview

This document defines a phased migration sequence for transitioning the Loan Service application from its legacy CDW schema to the modern normalized schema. Phases are ordered from **lowest risk to highest risk**, with explicit entry criteria, exit criteria, and rollback procedures for each phase.

---

## Migration Phases

### Phase 0: Preparation (Risk: None)

**Duration:** 1–2 days

**Objective:** Establish baseline measurements and tooling before any schema changes.

**Tasks:**

| # | Task | Owner | Status |
|---|------|-------|--------|
| 0.1 | Capture golden-file API responses for all 5 endpoints as baseline snapshots | Dev | Pending |
| 0.2 | Write automated regression test suite that compares API responses against golden files | Dev | Pending |
| 0.3 | Extract translation logic from `LoanService` into a dedicated `LegacyDataTranslator` class | Dev | Pending |
| 0.4 | Implement feature flag infrastructure (`datasource.mode` property: `legacy`, `modern`, `dual`) | Dev | Pending |
| 0.5 | Set up data reconciliation queries (row counts, sum of balances, etc.) | Dev | Pending |
| 0.6 | Create modern schema DDL as a Spring SQL init script (`schema-modern.sql`) | Dev | Pending |

**Entry Criteria:**
- Current application compiles and passes existing tests
- All 5 API endpoints return valid responses

**Exit Criteria:**
- Golden-file baseline captured for all endpoints
- Regression test suite runs green against legacy data source
- Feature flag toggles between legacy/modern/dual without error
- `LegacyDataTranslator` extracted and unit-tested

**Rollback:** N/A — no production changes.

---

### Phase 1: Product Catalog Migration (Risk: Very Low)

**Duration:** 1 day

**Objective:** Migrate the loan product reference data from `CDW_LN_PROD` to `loan_products`.

**Why Lowest Risk:**
- Reference data only — no transactional writes
- No PII
- Smallest surface area (5 records, 10 columns)
- No inbound FK dependencies
- Not directly exposed via API (consumed internally)

**Tasks:**

| # | Task | Owner | Status |
|---|------|-------|--------|
| 1.1 | Create `ModernLoanProduct` JPA entity with proper types (`Integer`, `BigDecimal`, `Boolean`, `LocalDate`) | Dev | Pending |
| 1.2 | Create `ModernLoanProductRepository` | Dev | Pending |
| 1.3 | Write product ETL migration: parse term months, min/max amounts, dates, status→boolean | Dev | Pending |
| 1.4 | Run ETL and validate: 5 products migrated, amounts and dates match | Dev | Pending |
| 1.5 | Update `LoanService` to read products from modern repository (behind feature flag) | Dev | Pending |
| 1.6 | Run golden-file regression in dual mode — confirm loan summaries show correct product descriptions | Dev | Pending |
| 1.7 | Switch feature flag to `modern` for products | Dev | Pending |

**Entry Criteria:**
- Phase 0 complete
- Modern `loan_products` DDL reviewed

**Exit Criteria:**
- All 5 products migrated with correct types
- `GET /api/loans` returns identical product descriptions in modern mode
- Golden-file regression passes

**Rollback:** Switch feature flag back to `legacy`. No data loss — legacy table untouched.

**Validation Queries:**
```sql
-- Row count parity
SELECT COUNT(*) FROM CDW_LN_PROD;          -- Expect: 5
SELECT COUNT(*) FROM loan_products;         -- Expect: 5

-- Term months type check
SELECT code, term_months FROM loan_products WHERE term_months IS NOT NULL;
```

---

### Phase 2: Borrower Identity Migration (Risk: Low)

**Duration:** 1–2 days

**Objective:** Migrate borrower records from `CDW_BORR_MSTR` to `borrowers`.

**Why Low Risk:**
- Independent entity (no outbound FKs)
- Small data set (5 borrowers)
- Clean API surface (2 endpoints)
- PII present but data stays within the same system boundary

**Tasks:**

| # | Task | Owner | Status |
|---|------|-------|--------|
| 2.1 | Create `ModernBorrower` JPA entity with proper types (`LocalDate dateOfBirth`, `Integer creditScore`, `BigDecimal annualIncome`) | Dev | Pending |
| 2.2 | Create `ModernBorrowerRepository` | Dev | Pending |
| 2.3 | Write borrower ETL: parse dates, credit scores, income amounts, expand status codes, preserve `external_id` mapping | Dev | Pending |
| 2.4 | Handle SSN migration: copy `BORR_SSN_ENCR → ssn_hash` (flag for re-encryption in production) | Dev | Pending |
| 2.5 | Run ETL and validate: 5 borrowers migrated, all fields match | Dev | Pending |
| 2.6 | Update `LoanService.toBorrowerDto()` to use modern entity (behind feature flag) | Dev | Pending |
| 2.7 | Run golden-file regression for `/api/borrowers` and `/api/borrowers/{id}` | Dev | Pending |
| 2.8 | Switch feature flag to `modern` for borrowers | Dev | Pending |

**Entry Criteria:**
- Phase 1 complete
- Modern `borrowers` DDL reviewed
- SSN handling strategy approved

**Exit Criteria:**
- All 5 borrowers migrated with correct types
- `GET /api/borrowers` returns identical JSON in modern mode
- `GET /api/borrowers/{id}` returns identical borrower details with loans attached
- Golden-file regression passes for borrower endpoints

**Rollback:** Switch feature flag back to `legacy`. Legacy table untouched.

**Validation Queries:**
```sql
-- Row count parity
SELECT COUNT(*) FROM CDW_BORR_MSTR;    -- Expect: 5
SELECT COUNT(*) FROM borrowers;          -- Expect: 5

-- Credit score type check (was VARCHAR, now INTEGER)
SELECT external_id, credit_score FROM borrowers;

-- Annual income type check (was comma-string, now DECIMAL)
SELECT external_id, annual_income FROM borrowers;
```

---

### Phase 3: Loan Account Migration (Risk: High)

**Duration:** 2–3 days

**Objective:** Migrate loan accounts from `CDW_LN_ACCT` to `loan_accounts`, resolving FK relationships and removing denormalized borrower data.

**Why High Risk:**
- Core business entity — largest surface area (25 columns)
- Denormalization removal (3 embedded borrower fields → FK)
- Two FK dependencies require prior phases to be complete
- FK resolution by natural key lookup (not direct ID mapping)
- 7+ amount fields and 5+ date fields to convert
- Multiple status and type code expansions
- Service layer contains the most translation logic for this entity

**Tasks:**

| # | Task | Owner | Status |
|---|------|-------|--------|
| 3.1 | Create `ModernLoanAccount` JPA entity with `@ManyToOne borrower`, `@ManyToOne product`, proper types | Dev | Pending |
| 3.2 | Create `ModernLoanAccountRepository` | Dev | Pending |
| 3.3 | Write loan account ETL | Dev | Pending |
| 3.3a | — Resolve `BORR_ID → borrowers.id` via `SELECT id FROM borrowers WHERE external_id = ?` | Dev | Pending |
| 3.3b | — Resolve `PROD_CD → loan_products.id` via `SELECT id FROM loan_products WHERE code = ?` | Dev | Pending |
| 3.3c | — Parse all amount fields (remove commas → BigDecimal) | Dev | Pending |
| 3.3d | — Parse all date fields (MM/DD/YYYY → LocalDate) | Dev | Pending |
| 3.3e | — Expand status codes (ACT→ACTIVE, CLO→CLOSED, DFT→DEFAULT, FRB→FORBEARANCE) | Dev | Pending |
| 3.3f | — Expand property type codes (SFR→Single Family, etc.) | Dev | Pending |
| 3.3g | — Drop denormalized borrower fields (BORR_FST_NM, BORR_LST_NM, BORR_SSN_LST4) | Dev | Pending |
| 3.4 | Validate ETL: 5 loan accounts migrated, all FK references resolve, amounts match | Dev | Pending |
| 3.5 | Update `LoanService.toLoanSummary()`: resolve borrower name via FK relationship | Dev | Pending |
| 3.6 | Remove `parseLegacyAmount`, `expandStatusCode`, `expandPropertyType` from service (modern entity handles types) | Dev | Pending |
| 3.7 | Run golden-file regression for `/api/loans` and `/api/loans/{id}` in dual mode | Dev | Pending |
| 3.8 | Run full regression including `/api/borrowers/{id}` (which attaches loans) | Dev | Pending |
| 3.9 | Switch feature flag to `modern` for loan accounts | Dev | Pending |

**Entry Criteria:**
- Phase 2 complete (borrowers available in modern schema for FK resolution)
- Phase 1 complete (products available in modern schema for FK resolution)
- ETL validated in test environment

**Exit Criteria:**
- All 5 loan accounts migrated with correct FKs and types
- No denormalized borrower data in modern table
- All API endpoints return identical JSON in modern mode
- Golden-file regression passes for all loan-related endpoints
- LTV percentages, escrow balances, and all monetary amounts match

**Rollback:** Switch feature flag back to `legacy`. Modern tables can be truncated and re-migrated.

**Validation Queries:**
```sql
-- Row count parity
SELECT COUNT(*) FROM CDW_LN_ACCT;       -- Expect: 5
SELECT COUNT(*) FROM loan_accounts;      -- Expect: 5

-- FK integrity check
SELECT la.account_number, b.external_id, lp.code
FROM loan_accounts la
JOIN borrowers b ON la.borrower_id = b.id
JOIN loan_products lp ON la.product_id = lp.id;

-- Amount parity (spot check)
SELECT account_number, original_amount, current_balance, monthly_payment
FROM loan_accounts;
```

---

### Phase 4: Payment History Migration (Risk: Medium-High)

**Duration:** 1–2 days

**Objective:** Migrate payment records from `CDW_PMT_HIST` to `payments`, linking to modern loan accounts.

**Why Medium-High Risk:**
- Financial transaction data — amounts must be exact
- FK dependency on loan accounts
- ID scheme changes (string sequence → auto-increment BIGINT)
- Multiple amount fields per record (principal, interest, escrow, late fee)

**Tasks:**

| # | Task | Owner | Status |
|---|------|-------|--------|
| 4.1 | Create `ModernPayment` JPA entity with `@ManyToOne loanAccount`, proper types | Dev | Pending |
| 4.2 | Create `ModernPaymentRepository` | Dev | Pending |
| 4.3 | Write payment ETL | Dev | Pending |
| 4.3a | — Resolve `LN_ACCT_NBR → loan_accounts.id` via `SELECT id FROM loan_accounts WHERE account_number = ?` | Dev | Pending |
| 4.3b | — Parse all amount fields (5 decimal columns) | Dev | Pending |
| 4.3c | — Parse all date fields (4 date columns) | Dev | Pending |
| 4.3d | — Expand payment type codes (REG→REGULAR, etc.) | Dev | Pending |
| 4.3e | — Expand payment status codes (PST→POSTED, etc.) | Dev | Pending |
| 4.4 | Validate ETL: 10 payments migrated, all FK references resolve, amounts sum correctly | Dev | Pending |
| 4.5 | Update `LoanService.getPaymentsByLoan()` to query modern repository | Dev | Pending |
| 4.6 | Remove `expandPaymentType`, `expandPaymentStatus` from service | Dev | Pending |
| 4.7 | Run golden-file regression for `/api/loans/{loanId}/payments` | Dev | Pending |
| 4.8 | Validate: sum of payments per loan matches between legacy and modern | Dev | Pending |
| 4.9 | Switch feature flag to `modern` for payments | Dev | Pending |

**Entry Criteria:**
- Phase 3 complete (loan accounts available in modern schema for FK resolution)

**Exit Criteria:**
- All 10 payments migrated with correct FKs and types
- Payment amounts match to the cent
- `GET /api/loans/{loanId}/payments` returns identical JSON in modern mode
- Sum of principal + interest + escrow + late fee = total amount for each payment
- Golden-file regression passes

**Rollback:** Switch feature flag back to `legacy`. Payment data can be re-migrated.

**Validation Queries:**
```sql
-- Row count parity
SELECT COUNT(*) FROM CDW_PMT_HIST;      -- Expect: 10
SELECT COUNT(*) FROM payments;           -- Expect: 10

-- Amount integrity check
SELECT p.id, p.total_amount,
       p.principal_amount + p.interest_amount + p.escrow_amount + p.late_fee AS computed_total
FROM payments p;

-- Payments-per-loan count
SELECT la.account_number, COUNT(p.id)
FROM loan_accounts la
JOIN payments p ON p.loan_account_id = la.id
GROUP BY la.account_number;
```

---

### Phase 5: Cleanup & Decommission (Risk: Low)

**Duration:** 1 day

**Objective:** Remove legacy code paths, entities, and schema. Finalize migration.

**Tasks:**

| # | Task | Owner | Status |
|---|------|-------|--------|
| 5.1 | Remove feature flag infrastructure (set modern as only mode) | Dev | Pending |
| 5.2 | Delete legacy entity classes (`LegacyBorrower`, `LegacyLoanAccount`, `LegacyLoanProduct`, `LegacyPayment`) | Dev | Pending |
| 5.3 | Delete legacy repository interfaces (`LegacyBorrowerRepository`, etc.) | Dev | Pending |
| 5.4 | Delete `LegacyDataTranslator` / remaining translation methods | Dev | Pending |
| 5.5 | Delete `schema-legacy.sql` and `data-legacy.sql` | Dev | Pending |
| 5.6 | Update `application.properties` to point to modern schema only | Dev | Pending |
| 5.7 | Update documentation (`README.md`, `MIGRATION_TASKS.md`) | Dev | Pending |
| 5.8 | Final golden-file regression run | Dev | Pending |
| 5.9 | Archive legacy schema documentation in `data/legacy-schema/` (keep for reference) | Dev | Pending |

**Entry Criteria:**
- All four domain migrations complete (Phases 1–4)
- Application running entirely on modern schema for a validation period
- All golden-file regressions pass

**Exit Criteria:**
- No legacy entity or repository classes remain in the codebase
- Application starts and runs using only modern schema
- All API endpoints return correct responses
- Documentation updated to reflect modern architecture

**Rollback:** Restore legacy files from git history if needed.

---

## Overall Timeline

```
Week 1                           Week 2
┌────────┬────────┬────────┬────────┬────────┐
│ Phase 0│Phase 1 │Phase 2 │ Phase 3         │
│ Prep   │Products│Borrower│ Loan Accounts   │
│ 1-2d   │ 1d     │ 1-2d   │ 2-3d            │
└────────┴────────┴────────┴────────┴────────┘

Week 3
┌────────┬────────┐
│Phase 4 │Phase 5 │
│Payments│Cleanup │
│ 1-2d   │ 1d     │
└────────┴────────┘

Total estimated duration: 8–12 working days
```

---

## Rollback Strategy

Every phase uses the **feature flag** as the primary rollback mechanism:

1. **Instant rollback:** Change `datasource.mode` property from `modern` to `legacy` and restart
2. **Data rollback:** Modern tables can be truncated and re-populated from legacy (legacy tables are never modified)
3. **Code rollback:** Each phase is a separate commit/PR — `git revert` restores the previous state
4. **Legacy tables preserved:** The legacy CDW tables remain intact and populated throughout all phases until Phase 5 cleanup

**Key principle:** Legacy data is **read-only** throughout the migration. No legacy records are modified or deleted until Phase 5.
