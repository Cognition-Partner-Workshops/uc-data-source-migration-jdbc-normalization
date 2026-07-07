# Data Quality Report — Legacy CDW Loan Data

This document describes the data quality validation framework added to
`loan-service` and summarizes the findings when it is run against the seeded
legacy data warehouse (CDW) tables.

## 1. Data Flow Under Validation

```
Legacy CDW tables (H2, all VARCHAR)         Validation framework          API surface
────────────────────────────────           ────────────────────          ───────────────
CDW_BORR_MSTR  ─┐                           BorrowerValidator      ┐
CDW_LN_PROD    ─┤   JPA entities            LoanProductValidator   │      /api/loans
CDW_LN_ACCT    ─┼─► Legacy* entities ─┬───► LoanAccountValidator   ├────► /api/borrowers
CDW_PMT_HIST   ─┘   (String fields)   │     PaymentValidator       │      /api/data-quality
                                      │                            │
                                      ▼                            ▼
                                 LoanService                 DataQualityService
                            (parses strings → typed     (scores every record,
                             DTO fields: BigDecimal,      aggregates per-table
                             Integer, expanded codes)     and overall report)
```

The legacy warehouse stores **everything as `VARCHAR`**: dates as `MM/DD/YYYY`
strings, money as comma-grouped strings, and statuses as short mnemonic codes.
`LoanService` coerces those strings into typed `LoanSummaryDto` / `BorrowerDto`
/ `PaymentDto` fields at read time. Any legacy value that cannot be coerced
would throw at request time (e.g. `new BigDecimal("N/A")`) or silently degrade
(e.g. an unknown status code passes through unexpanded). The validation
framework surfaces those risks **before** migration to the modern typed schema.

## 2. Validation Framework

Package `com.workshop.loanservice.validation`:

| Component | Responsibility |
|-----------|----------------|
| `Severity` | `ERROR` (25 pt), `WARNING` (10 pt), `INFO` (2 pt) score penalties |
| `ValidationIssue` | One finding: `table`, `recordId`, `field`, `ruleId`, `severity`, `message` |
| `LegacyValueChecks` | Reusable, side-effect-free checks (strict `MM/DD/YYYY`, comma-grouped amount, integer/decimal, email) and the valid code domains |
| `ReferenceData` | Lookup sets of borrower IDs / product codes / loan account numbers for referential-integrity rules |
| `RecordValidator<T>` | Contract implemented by each per-table validator |
| `BorrowerValidator`, `LoanProductValidator`, `LoanAccountValidator`, `PaymentValidator` | Per-table rule sets |

Package `com.workshop.loanservice.validation.report`:

| Component | Responsibility |
|-----------|----------------|
| `RecordQualityScore` | Per-record score = `100 − Σ(severity penalties)`, floored at 0 |
| `TableQualityReport` | Per-table totals, clean-record count, average score, issue counts by severity |
| `DataQualityReport` | Overall record-weighted score and per-table breakdown |
| `DataQualityService` | Builds `ReferenceData`, runs every validator, aggregates the report |

The report is also exposed as JSON at **`GET /api/data-quality`**
(`DataQualityController`).

### Scoring

Each record starts at 100. Every issue subtracts its severity penalty
(`ERROR` −25, `WARNING` −10, `INFO` −2); the record score is floored at 0. A
table's score is the mean of its record scores; the overall score is the
record-weighted mean across tables.

## 3. Validation Rules

### 3.1 `CDW_BORR_MSTR` → `borrowers`

| Rule ID | Field | Severity | Check |
|---------|-------|----------|-------|
| `BORR_ID_REQUIRED` | `BORR_ID` | ERROR | Not null/blank |
| `BORR_FST_NM_REQUIRED` | `BORR_FST_NM` | ERROR | Not null/blank |
| `BORR_LST_NM_REQUIRED` | `BORR_LST_NM` | ERROR | Not null/blank |
| `BORR_DOB_DT_FORMAT` | `BORR_DOB_DT` | ERROR | Required + valid `MM/DD/YYYY` |
| `BORR_CRET_DT_FORMAT` | `BORR_CRET_DT` | ERROR | Valid `MM/DD/YYYY` when present |
| `BORR_UPDT_DT_FORMAT` | `BORR_UPDT_DT` | ERROR | Valid `MM/DD/YYYY` when present |
| `BORR_CRDT_SCR_PRESENT` | `BORR_CRDT_SCR` | WARNING | Present |
| `BORR_CRDT_SCR_NUMERIC` | `BORR_CRDT_SCR` | ERROR | Parseable integer |
| `BORR_CRDT_SCR_RANGE` | `BORR_CRDT_SCR` | WARNING | Within 300–850 |
| `BORR_ANN_INCM_NUMERIC` | `BORR_ANN_INCM` | ERROR | Parseable amount (comma-grouped) |
| `BORR_STAT_CD_REQUIRED` | `BORR_STAT_CD` | ERROR | Not null/blank |
| `BORR_STAT_CD_VALID` | `BORR_STAT_CD` | WARNING | One of `ACT`, `INA` |
| `BORR_EMAIL_ADDR_FORMAT` | `BORR_EMAIL_ADDR` | WARNING | Well-formed email when present |

### 3.2 `CDW_LN_PROD` → `loan_products`

| Rule ID | Field | Severity | Check |
|---------|-------|----------|-------|
| `PROD_CD_REQUIRED` | `PROD_CD` | ERROR | Not null/blank |
| `PROD_DESC_TXT_REQUIRED` | `PROD_DESC_TXT` | ERROR | Not null/blank |
| `PROD_TERM_MOS_NUMERIC` | `PROD_TERM_MOS` | ERROR | Parseable integer when present |
| `PROD_MIN_AMT_NUMERIC` | `PROD_MIN_AMT` | ERROR | Parseable amount when present |
| `PROD_MAX_AMT_NUMERIC` | `PROD_MAX_AMT` | ERROR | Parseable amount when present |
| `PROD_AMT_RANGE` | `PROD_MIN_AMT` | WARNING | Min ≤ max |
| `PROD_STAT_CD_VALID` | `PROD_STAT_CD` | WARNING | One of `ACT`, `INA` |
| `PROD_EFF_DT_FORMAT` | `PROD_EFF_DT` | ERROR | Valid `MM/DD/YYYY` when present |
| `PROD_EXP_DT_FORMAT` | `PROD_EXP_DT` | ERROR | Valid `MM/DD/YYYY` when present |

### 3.3 `CDW_LN_ACCT` → `loan_accounts`

| Rule ID | Field | Severity | Check |
|---------|-------|----------|-------|
| `LN_ACCT_NBR_REQUIRED` | `LN_ACCT_NBR` | ERROR | Not null/blank |
| `LN_BORR_ID_REQUIRED` | `BORR_ID` | ERROR | Not null/blank |
| `LN_PROD_CD_REQUIRED` | `PROD_CD` | ERROR | Not null/blank |
| `LN_BORR_ID_FK` | `BORR_ID` | ERROR | **Referential integrity**: exists in `CDW_BORR_MSTR` |
| `LN_PROD_CD_FK` | `PROD_CD` | ERROR | **Referential integrity**: exists in `CDW_LN_PROD` |
| `LN_ORIG_AMT_NUMERIC` / `LN_CURR_BAL_NUMERIC` / `LN_PMT_AMT_NUMERIC` / `LN_ESCROW_BAL_NUMERIC` / `PROP_APRS_VAL_NUMERIC` | amounts | ERROR | Parseable amount when present |
| `LN_INT_RT_NUMERIC` / `LN_LTV_PCT_NUMERIC` | rate / LTV | ERROR | Parseable decimal when present |
| `LN_TERM_MOS_NUMERIC` / `LN_DLQ_DAYS_NUMERIC` | integers | ERROR | Parseable integer when present |
| `LN_ORIG_DT_FORMAT` / `LN_MAT_DT_FORMAT` / `LN_1ST_PMT_DT_FORMAT` / `LN_NXT_PMT_DT_FORMAT` / `LN_CRET_DT_FORMAT` / `LN_UPDT_DT_FORMAT` | dates | ERROR | Valid `MM/DD/YYYY` when present |
| `LN_STAT_CD_REQUIRED` | `LN_STAT_CD` | ERROR | Not null/blank |
| `LN_STAT_CD_VALID` | `LN_STAT_CD` | ERROR | One of `ACT`, `CLO`, `DFT`, `FRB` |
| `PROP_TYP_CD_VALID` | `PROP_TYP_CD` | WARNING | One of `SFR`, `CND`, `MFR`, `TWN` |

### 3.4 `CDW_PMT_HIST` → `payments`

| Rule ID | Field | Severity | Check |
|---------|-------|----------|-------|
| `PMT_SEQ_NBR_REQUIRED` | `PMT_SEQ_NBR` | ERROR | Not null/blank |
| `PMT_LN_ACCT_NBR_REQUIRED` | `LN_ACCT_NBR` | ERROR | Not null/blank |
| `PMT_LN_ACCT_NBR_FK` | `LN_ACCT_NBR` | ERROR | **Referential integrity**: exists in `CDW_LN_ACCT` |
| `PMT_AMT_NUMERIC` / `PMT_PRIN_AMT_NUMERIC` / `PMT_INT_AMT_NUMERIC` / `PMT_ESCROW_AMT_NUMERIC` / `PMT_LATE_FEE_NUMERIC` | amounts | ERROR | Parseable amount when present |
| `PMT_DT_FORMAT` | `PMT_DT` | ERROR | Required + valid `MM/DD/YYYY` |
| `PMT_RECV_DT_FORMAT` / `PMT_PROC_DT_FORMAT` / `PMT_CRET_DT_FORMAT` / `PMT_UPDT_DT_FORMAT` | dates | ERROR | Valid `MM/DD/YYYY` when present |
| `PMT_TYP_CD_REQUIRED` / `PMT_TYP_CD_VALID` | `PMT_TYP_CD` | ERROR | Present + one of `REG`, `EXT`, `PRT`, `PRE` |
| `PMT_STAT_CD_REQUIRED` / `PMT_STAT_CD_VALID` | `PMT_STAT_CD` | ERROR | Present + one of `PST`, `REV`, `NSF`, `PND` |

Valid-date checks use a **strict** resolver, so impossible calendar dates such
as `02/30/2020` and non-`MM/DD/YYYY` formats (e.g. ISO `2020-02-15` or
non-zero-padded `2/5/2020`) are rejected even though they are the right length.

## 4. Findings (Seeded Data)

Running `DataQualityService.generateReport()` against the seed data in
`src/main/resources/data-legacy.sql`:

| Table | Records | Clean | Avg score | Errors | Warnings | Info |
|-------|--------:|------:|----------:|-------:|---------:|-----:|
| `CDW_BORR_MSTR` | 5 | 5 | 100.0 | 0 | 0 | 0 |
| `CDW_LN_PROD` | 5 | 5 | 100.0 | 0 | 0 | 0 |
| `CDW_LN_ACCT` | 5 | 5 | 100.0 | 0 | 0 | 0 |
| `CDW_PMT_HIST` | 10 | 10 | 100.0 | 0 | 0 | 0 |
| **Overall** | **25** | **25** | **100.0** | **0** | **0** | **0** |

**The current seed data is clean** — every date is a valid `MM/DD/YYYY`, every
amount parses after comma stripping, all status/type codes are in their allowed
domains, and every `CDW_LN_ACCT.BORR_ID` / `PROD_CD` and every
`CDW_PMT_HIST.LN_ACCT_NBR` resolves to a parent row. This is asserted by
`DataQualityServiceTest` (0 errors, 25 clean records, overall score 100).

The framework's ability to *catch* dirty data is proven by the per-validator
unit tests (`BorrowerValidatorTest`, `LoanAccountValidatorTest`,
`PaymentValidatorTest`, `LoanProductValidatorTest`), which feed malformed
records (missing required fields, `1978-03-15` dates, `EXCELLENT` credit
scores, orphaned FKs, invalid status codes, unparseable amounts) and assert the
expected issues fire.

### Interpreting real-world data

When run against production CDW extracts (rather than the curated seed), expect
the highest-value findings to be:

1. **Unparseable amounts / dates** (ERROR) — these throw in `LoanService` today
   and would block a record from migrating; they must be cleansed first.
2. **Orphaned foreign keys** (ERROR) — loan accounts pointing at missing
   borrowers/products, or payments pointing at missing loans; these break FK
   resolution in the modern normalized schema.
3. **Out-of-domain status/type codes** (ERROR/WARNING) — codes outside the
   documented sets pass through `LoanService` unexpanded and would land as
   unmapped values in the modern `status`/`type` columns.

## 5. Schema Contract Tests

`ApiSchemaContractTest` pins the JSON shape and types of the public API so a
data-source migration cannot silently change the contract:

- **`GET /api/loans`** — array of 5; string fields (`loanAccountNumber`,
  `borrowerName`, `productDescription`, `status`, `originationDate`,
  `propertyAddress`, `propertyType`) and numeric fields (`originalAmount`,
  `currentBalance`, `interestRate`, `monthlyPayment`).
- **`GET /api/loans/{id}`** — verifies status expansion (`ACT` → `Active`),
  property-type expansion (`SFR` → `Single Family Residence`), and parsed
  numeric values.
- **`GET /api/borrowers`** — array of 5; string fields plus numeric
  `creditScore`.
- **`GET /api/borrowers/{id}`** — includes a typed nested `loans` array.
- **`GET /api/data-quality`** — report shape (`totalRecords`, `overallScore`,
  `errorCount`, `tables[]`).

## 6. Running

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # project targets Java 17
mvn test                 # runs validator, service, and contract tests
mvn spring-boot:run      # then GET http://localhost:8080/api/data-quality
```
