# Data Anomaly Report — Legacy CDW Tables

> Audit of `src/main/resources/schema-legacy.sql` and `src/main/resources/data-legacy.sql`

---

## DA-001: Payment Component Mismatch (Principal + Interest + Escrow != Total)

| Field | Value |
|-------|-------|
| **Severity** | Critical |
| **Table** | `CDW_PMT_HIST` |
| **Columns** | `PMT_AMT`, `PMT_PRIN_AMT`, `PMT_INT_AMT`, `PMT_ESCROW_AMT` |

**Example Bad Records:**

| PMT_SEQ_NBR | PMT_AMT (Total) | PRIN + INT + ESCROW | Delta |
|---|---|---|---|
| `PMT-2025120001` | 1,487.02 | 456.78 + 1,074.69 + 355.55 = 1,887.02 | **+400.00** |
| `PMT-2025110001` | 1,487.02 | 454.97 + 1,076.50 + 355.55 = 1,887.02 | **+400.00** |

Both affected payments belong to loan `LN-2019-00142` (James Mitchell). All other loan payments sum correctly.

**Business Impact:** Financial reporting will show incorrect payment breakdowns. Downstream accounting, amortization schedules, and escrow analysis will be materially wrong. Regulatory reports (TILA, RESPA) could contain inaccurate figures.

**Recommended Fix:** Add a payment-component validation that asserts `principal + interest + escrow + late_fee >= total` (within a tolerance). Flag or reject records where the delta exceeds a threshold (e.g., $0.02 rounding tolerance). Investigate the source system to determine whether the total or the components are authoritative.

---

## DA-002: Unhandled NumberFormatException on Malformed Numeric Strings

| Field | Value |
|-------|-------|
| **Severity** | Critical |
| **Table** | All tables (`CDW_BORR_MSTR`, `CDW_LN_ACCT`, `CDW_PMT_HIST`) |
| **Columns** | `BORR_CRDT_SCR`, `BORR_ANN_INCM`, `LN_ORIG_AMT`, `LN_CURR_BAL`, `LN_INT_RT`, `PMT_AMT`, etc. |

**Example Bad Records (hypothetical but unguarded):**

All numeric values in the legacy schema are `VARCHAR`. The service layer methods `parseLegacyAmount()`, `parseLegacyDecimal()`, and `parseLegacyInteger()` call `new BigDecimal(...)` and `Integer.parseInt(...)` without try-catch. Any non-numeric value (e.g., `"N/A"`, `"$285,000"`, `"TBD"`, empty string with whitespace) will throw an unchecked `NumberFormatException`, crashing the API endpoint with a 500 error.

Current seed data is clean, but the schema allows arbitrary VARCHAR content. In production CDW systems, placeholder values like `"N/A"`, `"PENDING"`, or `"---"` are commonly observed.

**Business Impact:** A single malformed record in the legacy warehouse causes the entire `/api/loans` or `/api/borrowers` endpoint to return HTTP 500, making the service unavailable. Individual record lookups also fail catastrophically.

**Recommended Fix:** Wrap all parsing methods in try-catch blocks. Return sensible defaults (e.g., `BigDecimal.ZERO`, `null`) on parse failure, and log the anomaly with record identifiers for triage.

---

## DA-003: No Foreign Key Constraints — Orphaned Record Risk

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Table** | `CDW_LN_ACCT`, `CDW_PMT_HIST` |
| **Columns** | `CDW_LN_ACCT.BORR_ID`, `CDW_LN_ACCT.PROD_CD`, `CDW_PMT_HIST.LN_ACCT_NBR` |

**Example Bad Records:**

The legacy schema defines zero foreign key constraints. Any of the following can exist:
- A loan account with `BORR_ID = 'B-99999'` (non-existent borrower)
- A loan account with `PROD_CD = 'INVALID'` (non-existent product)
- A payment with `LN_ACCT_NBR = 'LN-0000-00000'` (non-existent loan)

In `LoanService.toLoanSummary()`, when `products.get(acct.getProductCode())` returns `null` for an invalid product code, the code falls back to the raw code string — silently hiding the data integrity issue. In `getBorrowerById()`, orphaned loans would be excluded from the borrower's loan list without notification.

**Business Impact:** Orphaned records silently produce incomplete or misleading API responses. A borrower detail page may show zero loans despite active accounts existing, or loan summaries may display raw product codes instead of descriptions, confusing downstream consumers.

**Recommended Fix:** Validate foreign key references at ingestion time. Check that `BORR_ID` maps to an existing borrower, `PROD_CD` maps to an existing product, and `LN_ACCT_NBR` maps to an existing loan before processing. Log warnings for orphaned records.

---

## DA-004: Denormalized Borrower Data Inconsistency Risk

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Table** | `CDW_LN_ACCT` vs `CDW_BORR_MSTR` |
| **Columns** | `CDW_LN_ACCT.BORR_FST_NM`, `CDW_LN_ACCT.BORR_LST_NM`, `CDW_LN_ACCT.BORR_SSN_LST4` |

**Example Bad Records:**

The `CDW_LN_ACCT` table embeds denormalized copies of borrower data (`BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4`). These can drift from the canonical `CDW_BORR_MSTR` record if the master is updated without syncing the loan table. In `LoanService.toLoanSummary()`, the borrower name is sourced from `CDW_LN_ACCT` (line 106), not `CDW_BORR_MSTR` — so a name change on the master will not be reflected in loan summaries.

Current seed data is consistent, but this is a structural vulnerability inherent to the denormalized design.

**Business Impact:** API responses may show stale borrower names on loan records. Customer-facing statements and loan documents could display outdated or incorrect names.

**Recommended Fix:** In the service layer, cross-reference borrower data from `CDW_BORR_MSTR` instead of trusting denormalized fields. Add a consistency check that flags discrepancies between the two tables.

---

## DA-005: SSN Last-4 Values Derived from Phone Numbers, Not SSNs

| Field | Value |
|-------|-------|
| **Severity** | High |
| **Table** | `CDW_LN_ACCT` |
| **Column** | `BORR_SSN_LST4` |

**Example Bad Records:**

| Borrower | BORR_SSN_LST4 | BORR_PH_NBR (from CDW_BORR_MSTR) |
|---|---|---|
| B-10001 (Mitchell) | `0142` | `217-555-0142` |
| B-10002 (Chen) | `0198` | `503-555-0198` |
| B-10003 (Torres) | `0167` | `512-555-0167` |
| B-10004 (Johnson) | `0134` | `303-555-0134` |
| B-10005 (Williams) | `0156` | `602-555-0156` |

Every `BORR_SSN_LST4` value matches the last 4 digits of the borrower's phone number. This indicates the seed data generator used phone numbers to populate SSN fields rather than independent SSN values.

**Business Impact:** SSN-based identity verification at the loan level would match phone numbers instead of actual SSNs. Any KYC (Know Your Customer) or fraud-detection logic relying on `BORR_SSN_LST4` will produce false positives or mismatches.

**Recommended Fix:** Validate that `BORR_SSN_LST4` is a 4-digit numeric string. Cross-reference against the encrypted SSN in `CDW_BORR_MSTR.BORR_SSN_ENCR` if a decryption path exists. Flag records where the SSN last-4 suspiciously matches other PII fields.

---

## DA-006: Date String Ordering Produces Incorrect Chronological Sort

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Table** | `CDW_PMT_HIST` |
| **Column** | `PMT_DT` |

**Example Bad Records:**

The repository method `findByLoanAccountNumberOrderByPaymentDateDesc` sorts `PMT_DT` as a string. The `MM/DD/YYYY` format sorts lexicographically, not chronologically:

- `"12/15/2025"` > `"11/15/2025"` (correct by coincidence)
- `"02/15/2025"` < `"11/15/2025"` (incorrect — February sorts before November due to `0` < `1`)

With the current seed data (only Nov/Dec 2025 payments), this does not manifest. However, when historical payments spanning multiple months are loaded, the sort order will be wrong.

**Business Impact:** Payment history displayed to users or fed to amortization calculators will be out of order, leading to incorrect running-balance calculations and confusing customer statements.

**Recommended Fix:** Parse `PMT_DT` into a `LocalDate` in the service layer, and sort in-memory after retrieval. Alternatively, store dates in ISO-8601 format (`YYYY-MM-DD`) which sorts correctly as a string.

---

## DA-007: Delinquency Days Inconsistent with Status Code

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Table** | `CDW_LN_ACCT` |
| **Columns** | `LN_DLQ_DAYS`, `LN_STAT_CD` |

**Example Bad Records:**

| LN_ACCT_NBR | LN_DLQ_DAYS | LN_STAT_CD | Issue |
|---|---|---|---|
| `LN-2018-00089` | `15` | `ACT` | 15 days delinquent but status is "Active" |

Industry standard: loans past 30 days delinquent should typically be flagged for collections. Even at 15 days, the status should be qualified (e.g., "Active - Past Due") or the delinquency should trigger a workflow.

**Business Impact:** Risk management dashboards that rely on `LN_STAT_CD` alone will miss delinquent loans. Portfolio risk is understated when delinquent loans appear as "Active."

**Recommended Fix:** Add cross-field validation: if `LN_DLQ_DAYS > 0` and `LN_STAT_CD = 'ACT'`, flag the record or auto-adjust the status. Include the delinquency days in the API response (currently omitted from `LoanSummaryDto`).

---

## DA-008: Null Value Risk in Required Name Fields

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Table** | `CDW_BORR_MSTR`, `CDW_LN_ACCT` |
| **Columns** | `BORR_FST_NM`, `BORR_LST_NM` |

**Example Bad Records:**

The schema defines `BORR_FST_NM` and `BORR_LST_NM` as nullable `VARCHAR(50)`. Current seed data has all names populated, but the schema allows null values. In `LoanService`:

- `toBorrowerDto()` line 124: `borrower.getFirstName() + middle + " " + borrower.getLastName()` — produces `"null null"` if either field is null.
- `toLoanSummary()` line 106: `acct.getBorrowerFirstName() + " " + acct.getBorrowerLastName()` — same issue.

**Business Impact:** Null names propagate as literal `"null null"` strings in API responses and customer-facing UIs.

**Recommended Fix:** Add null checks before string concatenation. Use fallback values like `"[Unknown]"` for missing names. Reject or flag records where first name or last name is null, since these are required for any meaningful loan operation.

---

## DA-009: Annual Income Comma-Formatted Strings — Locale-Dependent Parsing Risk

| Field | Value |
|-------|-------|
| **Severity** | Low |
| **Table** | `CDW_BORR_MSTR` |
| **Column** | `BORR_ANN_INCM` |

**Example Records:**

| BORR_ID | BORR_ANN_INCM |
|---|---|
| B-10001 | `92,500` |
| B-10002 | `125,000` |
| B-10003 | `78,000` |

The `parseLegacyAmount()` method strips commas before parsing. However, European-style formatting (e.g., `92.500` using period as thousands separator) or currency symbols (e.g., `$92,500`) would cause a `NumberFormatException`. Additionally, the `BorrowerDto` does not expose annual income — the field is parsed but never used in the current API responses.

**Business Impact:** Low immediate impact since income is not exposed in the API. However, if income-based business rules are added (e.g., debt-to-income ratio checks), malformed values would cause failures.

**Recommended Fix:** Normalize income values by stripping currency symbols, whitespace, and handling locale-specific formatting. Consider exposing the parsed income in the `BorrowerDto` for completeness.

---

## DA-010: Raw Date Strings Passed Through to API Without Validation

| Field | Value |
|-------|-------|
| **Severity** | Medium |
| **Table** | All tables |
| **Columns** | All `*_DT` columns (`BORR_DOB_DT`, `LN_ORIG_DT`, `PMT_DT`, etc.) |

**Example:**

Dates are stored as `MM/DD/YYYY` strings and passed through to DTOs without parsing:
- `LoanSummaryDto.originationDate` receives the raw string `"02/15/2019"`
- `PaymentDto.paymentDate` receives the raw string `"12/15/2025"`

No validation ensures the format is correct. Values like `"13/32/2025"`, `"00/00/0000"`, or `"TBD"` would silently pass through to API consumers.

**Business Impact:** API consumers cannot rely on a consistent date format. Invalid dates propagated to frontend UIs or downstream systems cause display errors or calculation failures.

**Recommended Fix:** Parse all date strings into `LocalDate` using `DateTimeFormatter.ofPattern("MM/dd/yyyy")` at ingestion time. Catch `DateTimeParseException` and provide a fallback or flag invalid dates. Return dates in ISO-8601 format in the API response.
