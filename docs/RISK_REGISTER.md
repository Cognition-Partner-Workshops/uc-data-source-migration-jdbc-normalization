# Risk Register

## Overview

This document catalogs the top risks associated with migrating the Loan Service application from the legacy CDW schema to the modern normalized schema, along with their probability, impact, and mitigations.

---

## Risk Severity Matrix

| | **Low Impact** | **Medium Impact** | **High Impact** | **Critical Impact** |
|---|---|---|---|---|
| **High Probability** | Medium | High | Critical | Critical |
| **Medium Probability** | Low | Medium | High | Critical |
| **Low Probability** | Low | Low | Medium | High |

---

## Risk Register

### R-01: Financial Amount Parsing Errors

| Attribute | Value |
|-----------|-------|
| **Category** | Data Integrity |
| **Phase** | Phases 1–4 (all ETL phases) |
| **Probability** | Medium |
| **Impact** | Critical |
| **Severity** | **Critical** |

**Description:**
Legacy amount fields are stored as comma-formatted VARCHAR strings (e.g., `"285,000"`, `"1,487.02"`, `"0.00"`). During ETL, these must be parsed to `BigDecimal`. Malformed data, leading/trailing whitespace, or unexpected formats (e.g., `"$285,000"`, `"N/A"`, empty strings) could cause parsing failures or silent precision loss.

**Affected Fields:**
- `LN_ORIG_AMT`, `LN_CURR_BAL`, `LN_PMT_AMT`, `LN_ESCROW_BAL`, `PROP_APRS_VAL` (loan accounts — 5 fields)
- `PMT_AMT`, `PMT_PRIN_AMT`, `PMT_INT_AMT`, `PMT_ESCROW_AMT`, `PMT_LATE_FEE` (payments — 5 fields)
- `PROD_MIN_AMT`, `PROD_MAX_AMT` (products — 2 fields)
- `BORR_ANN_INCM` (borrowers — 1 field)

**Mitigations:**
1. **Pre-migration data profiling:** Run validation queries against all legacy amount fields to identify non-standard formats before ETL begins
2. **Defensive parsing:** Use try-catch with detailed error logging in ETL. Never silently swallow parse exceptions
3. **Post-migration reconciliation:** Compare `SUM(amount)` across legacy and modern tables for every amount field
4. **Golden-file regression:** API response amounts are verified field-by-field against baseline snapshots
5. **Dual-read mode:** In production, temporarily read from both sources and alert on any discrepancy

---

### R-02: Foreign Key Resolution Failures

| Attribute | Value |
|-----------|-------|
| **Category** | Data Integrity |
| **Phase** | Phase 3 (Loan Accounts), Phase 4 (Payments) |
| **Probability** | Low |
| **Impact** | High |
| **Severity** | **Medium** |

**Description:**
Modern loan accounts require FK references to `borrowers.id` and `loan_products.id`. These are resolved by natural key lookup (`BORR_ID → external_id`, `PROD_CD → code`). If a legacy loan references a borrower or product that wasn't migrated (orphan reference), the FK insert will fail.

Similarly, modern payments require FK to `loan_accounts.id` resolved via `LN_ACCT_NBR → account_number`.

**Mitigations:**
1. **Pre-migration integrity check:** Query for orphan references before ETL
   ```sql
   -- Orphan borrower references
   SELECT BORR_ID FROM CDW_LN_ACCT
   WHERE BORR_ID NOT IN (SELECT BORR_ID FROM CDW_BORR_MSTR);

   -- Orphan product references
   SELECT PROD_CD FROM CDW_LN_ACCT
   WHERE PROD_CD NOT IN (SELECT PROD_CD FROM CDW_LN_PROD);

   -- Orphan loan references in payments
   SELECT LN_ACCT_NBR FROM CDW_PMT_HIST
   WHERE LN_ACCT_NBR NOT IN (SELECT LN_ACCT_NBR FROM CDW_LN_ACCT);
   ```
2. **Strict migration ordering:** Always migrate in dependency order (products → borrowers → loans → payments)
3. **ETL error handling:** Log and quarantine records with unresolvable FKs rather than failing the entire batch
4. **Constraint validation:** Run FK integrity checks after each ETL phase

---

### R-03: Date Format Parsing Inconsistencies

| Attribute | Value |
|-----------|-------|
| **Category** | Data Integrity |
| **Phase** | Phases 1–4 |
| **Probability** | Medium |
| **Impact** | Medium |
| **Severity** | **Medium** |

**Description:**
Legacy dates are stored as `MM/DD/YYYY` strings. The ETL must parse these to `LocalDate` or `Timestamp`. Risks include:
- Ambiguous dates (e.g., `01/02/2020` — is it Jan 2 or Feb 1?)
- Null or empty date strings
- Dates with leading zeros stripped or inconsistent formatting
- Dates outside valid ranges

**Affected Fields:** 15+ date fields across all 4 tables (DOB, origination, maturity, payment dates, effective dates, etc.)

**Mitigations:**
1. **Explicit format specification:** Use `DateTimeFormatter.ofPattern("MM/dd/yyyy")` — never rely on default locale parsing
2. **Null handling:** Map empty/null strings to `NULL` in the modern schema
3. **Range validation:** Flag dates before 1900 or after 2100 as suspect
4. **Seed data review:** Current seed data uses consistent `MM/DD/YYYY` format — verify production data follows the same pattern

---

### R-04: API Response Contract Breakage

| Attribute | Value |
|-----------|-------|
| **Category** | Backward Compatibility |
| **Phase** | Phases 1–4 |
| **Probability** | Medium |
| **Impact** | High |
| **Severity** | **High** |

**Description:**
The REST API contract must remain identical throughout migration. Risk areas:
- Borrower name format changes (currently assembled from `acct.getBorrowerFirstName() + " " + acct.getBorrowerLastName()` from denormalized data — after migration, must resolve via FK relationship and produce identical output)
- Date formats in API responses (legacy stores and returns `MM/DD/YYYY` strings — if modern entities return `LocalDate` objects, JSON serialization may change to `YYYY-MM-DD`)
- Numeric precision differences (legacy `"1,487.02"` parsed to `BigDecimal` vs. modern `DECIMAL(10,2)` — ensure scale matches)
- Property address concatenation logic may behave differently if any fields are null

**Mitigations:**
1. **Golden-file regression testing:** Mandatory before each phase cutover
2. **DTO layer as contract guard:** DTOs already define the API shape — ensure all DTO fields are populated identically
3. **Date format pinning:** Configure Jackson `ObjectMapper` to serialize dates in the expected format
4. **Null-safe string handling:** Test with records that have null optional fields (e.g., `address_line2`, `middle_initial`)

---

### R-05: Denormalization Removal Causes Data Inconsistency

| Attribute | Value |
|-----------|-------|
| **Category** | Data Integrity |
| **Phase** | Phase 3 (Loan Accounts) |
| **Probability** | Low |
| **Impact** | High |
| **Severity** | **Medium** |

**Description:**
The legacy `CDW_LN_ACCT` table contains denormalized copies of borrower fields (`BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4`). These may diverge from the master records in `CDW_BORR_MSTR` if the borrower updated their name or the data was entered inconsistently. After migration, the modern schema uses only the FK reference — the denormalized copies are dropped.

If the denormalized data and the master data disagree, the API will start returning the master record's values, which could be perceived as a data change.

**Mitigations:**
1. **Pre-migration consistency check:**
   ```sql
   SELECT a.LN_ACCT_NBR, a.BORR_FST_NM, b.BORR_FST_NM,
          a.BORR_LST_NM, b.BORR_LST_NM
   FROM CDW_LN_ACCT a
   JOIN CDW_BORR_MSTR b ON a.BORR_ID = b.BORR_ID
   WHERE a.BORR_FST_NM != b.BORR_FST_NM
      OR a.BORR_LST_NM != b.BORR_LST_NM;
   ```
2. **Document known discrepancies:** If any exist, decide whether to use master or denormalized values as the source of truth
3. **Audit trail:** Log any discrepancies found during ETL for business review

---

### R-06: SSN/PII Exposure During Migration

| Attribute | Value |
|-----------|-------|
| **Category** | Security |
| **Phase** | Phase 2 (Borrower Identity) |
| **Probability** | Low |
| **Impact** | Critical |
| **Severity** | **High** |

**Description:**
The legacy `BORR_SSN_ENCR` field contains encrypted SSN data. During ETL, this data is read and written to the modern `ssn_hash` column. Risks include:
- Decrypted SSN values appearing in logs or error messages
- ETL intermediate state exposing SSN in plain text
- Migration scripts committed to version control with SSN data

**Mitigations:**
1. **Never log PII:** ETL code must not log SSN values, even in debug mode
2. **Direct copy:** Copy `BORR_SSN_ENCR → ssn_hash` as an opaque string — do not decrypt during ETL
3. **Re-encryption plan:** Schedule a separate, security-reviewed re-encryption pass after migration
4. **Seed data safety:** Current seed data uses placeholder values (`ENC_XXX_001`) — verify production data handling separately
5. **Code review gate:** Require security review for any code that touches SSN fields

---

### R-07: Dual-Read Mode Performance Degradation

| Attribute | Value |
|-----------|-------|
| **Category** | Performance |
| **Phase** | All migration phases |
| **Probability** | Medium |
| **Impact** | Low |
| **Severity** | **Low** |

**Description:**
During migration, dual-read mode executes queries against both legacy and modern data sources for comparison. This doubles the database load for every API call.

**Mitigations:**
1. **Time-box dual-read:** Enable only during validation windows, not permanently
2. **Async comparison:** Run the modern query asynchronously and compare results in background — don't block the API response on dual-read
3. **Sampling:** In production, compare a configurable percentage of requests rather than all of them
4. **Current H2 context:** With in-memory H2 and a 25-record dataset, performance is a non-issue. This risk is relevant for production-scale deployment

---

### R-08: ETL Idempotency Failures

| Attribute | Value |
|-----------|-------|
| **Category** | Operational |
| **Phase** | Phases 1–4 |
| **Probability** | Medium |
| **Impact** | Medium |
| **Severity** | **Medium** |

**Description:**
If the ETL process fails mid-execution and is restarted, duplicate records could be created in the modern schema (especially for auto-increment ID columns). Similarly, re-running the ETL without clearing the target tables could produce duplicates.

**Mitigations:**
1. **Idempotent ETL design:** Use `INSERT ... ON CONFLICT DO NOTHING` or check for existing records by natural key before inserting
2. **Unique constraints:** Modern schema already has unique constraints on natural keys (`external_id`, `account_number`, `code`)
3. **Transactional ETL:** Wrap each table's migration in a single transaction — all-or-nothing
4. **Clear-and-reload:** For the workshop/H2 context, truncate modern tables before re-running ETL

---

### R-09: Test Coverage Gap

| Attribute | Value |
|-----------|-------|
| **Category** | Quality |
| **Phase** | All phases |
| **Probability** | High |
| **Impact** | Medium |
| **Severity** | **High** |

**Description:**
The current test suite consists of a single `LoanServiceApplicationTests` class that only verifies the application context loads. There are no unit tests for the service layer, no integration tests for the API endpoints, and no tests for the type-conversion logic. This means migration regressions could go undetected.

**Mitigations:**
1. **Phase 0 priority:** Writing the golden-file regression suite is the first task before any migration work begins
2. **Unit tests for ETL:** Each type-conversion function (date parsing, amount parsing, status expansion) must have dedicated unit tests with edge cases
3. **Integration tests:** API endpoint tests that verify response shapes and values
4. **Acceptance criteria:** No phase is complete until its regression tests pass

---

### R-10: Legacy Schema Documentation Drift

| Attribute | Value |
|-----------|-------|
| **Category** | Knowledge |
| **Phase** | Phase 5 (Cleanup) |
| **Probability** | Medium |
| **Impact** | Low |
| **Severity** | **Low** |

**Description:**
After decommissioning legacy entities and schema files, knowledge of the legacy system's quirks (cryptic column names, status codes, denormalization patterns) may be lost. This could be problematic if rollback is needed or if other systems still reference the CDW schema.

**Mitigations:**
1. **Archive, don't delete:** Move legacy schema docs and column mappings to a `docs/legacy-archive/` directory rather than deleting them
2. **Commit history:** Git history preserves all legacy code — document the final legacy commit hash in migration notes
3. **Column mapping preservation:** Keep `data/mappings/column_mappings.md` as permanent documentation

---

## Risk Summary Dashboard

| ID | Risk | Severity | Phase | Status |
|----|------|----------|-------|--------|
| R-01 | Financial Amount Parsing Errors | **Critical** | 1–4 | Open |
| R-02 | FK Resolution Failures | Medium | 3–4 | Open |
| R-03 | Date Format Parsing Inconsistencies | Medium | 1–4 | Open |
| R-04 | API Response Contract Breakage | **High** | 1–4 | Open |
| R-05 | Denormalization Data Inconsistency | Medium | 3 | Open |
| R-06 | SSN/PII Exposure During Migration | **High** | 2 | Open |
| R-07 | Dual-Read Performance Degradation | Low | All | Open |
| R-08 | ETL Idempotency Failures | Medium | 1–4 | Open |
| R-09 | Test Coverage Gap | **High** | All | Open |
| R-10 | Legacy Documentation Drift | Low | 5 | Open |

---

## Top 3 Risks Requiring Immediate Action

1. **R-01 (Financial Amount Parsing):** Run data profiling queries against all legacy amount fields before any ETL. Implement defensive parsing with per-field validation
2. **R-09 (Test Coverage Gap):** Build the golden-file regression suite in Phase 0 before any migration work begins
3. **R-04 (API Contract Breakage):** Lock the DTO layer as the contract boundary. All migration changes must produce identical DTO output verified by golden-file comparison
