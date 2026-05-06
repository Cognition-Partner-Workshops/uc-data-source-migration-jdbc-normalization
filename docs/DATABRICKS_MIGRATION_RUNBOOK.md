# Databricks Migration Runbook

End-to-end guide for migrating from the legacy CDW (Corporate Data Warehouse) schema to a modern Delta Lake schema on Databricks.

---

## 1. Overview

| Aspect | Legacy (CDW) | Modern (Delta Lake) |
|--------|-------------|---------------------|
| Storage | Flat files / RDBMS, all VARCHAR | Delta Lake on DBFS/S3 |
| Typing | Everything is `VARCHAR` | `DATE`, `DECIMAL`, `INT`, `BOOLEAN`, `TIMESTAMP` |
| Naming | Cryptic abbreviations (`BORR_FST_NM`, `LN_CURR_BAL`) | Readable names (`first_name`, `current_balance`) |
| Structure | Denormalized (borrower data embedded in loan accounts) | Normalized with FK constraints |
| Integrity | No foreign keys, no constraints | Delta Lake constraints + FK relationships |
| Status codes | Abbreviations (`ACT`, `CLO`, `DFT`, `FRB`) | Full strings (`ACTIVE`, `CLOSED`, `DEFAULT`, `FORBEARANCE`) |

---

## 2. Source Tables

| Legacy Table | Record Count | Description |
|-------------|-------------|-------------|
| `CDW_BORR_MSTR` | 5 | Borrower master records |
| `CDW_LN_PROD` | 5 | Loan product catalog |
| `CDW_LN_ACCT` | 5 | Loan accounts (denormalized, includes borrower fields) |
| `CDW_PMT_HIST` | 10 | Payment history |

---

## 3. Target Tables

All target tables live in the `loan_warehouse` database (Unity Catalog schema).

| Target Table | Partitioned By | Source |
|-------------|---------------|--------|
| `loan_warehouse.borrowers` | — | `CDW_BORR_MSTR` |
| `loan_warehouse.loan_products` | — | `CDW_LN_PROD` |
| `loan_warehouse.loan_accounts` | `status` | `CDW_LN_ACCT` |
| `loan_warehouse.payments` | `payment_year` | `CDW_PMT_HIST` |

---

## 4. Column Mappings

### 4.1 CDW_BORR_MSTR → borrowers

| Legacy Column | Modern Column | Type Change | Transformation |
|---------------|---------------|-------------|----------------|
| `BORR_ID` | `external_id` | VARCHAR→STRING | Direct copy |
| `BORR_FST_NM` | `first_name` | VARCHAR→STRING | Direct copy |
| `BORR_LST_NM` | `last_name` | VARCHAR→STRING | Direct copy |
| `BORR_MID_INIT` | `middle_initial` | VARCHAR→STRING | Direct copy |
| `BORR_SSN_ENCR` | `ssn_hash` | VARCHAR→STRING | Direct copy (re-encrypt recommended) |
| `BORR_DOB_DT` | `date_of_birth` | VARCHAR→DATE | Parse `MM/DD/YYYY` |
| `BORR_ADDR_LN1` | `address_line1` | VARCHAR→STRING | Direct copy |
| `BORR_ADDR_LN2` | `address_line2` | VARCHAR→STRING | Direct copy |
| `BORR_CTY_NM` | `city` | VARCHAR→STRING | Direct copy |
| `BORR_ST_CD` | `state` | VARCHAR→STRING | Direct copy |
| `BORR_ZIP_CD` | `zip_code` | VARCHAR→STRING | Direct copy |
| `BORR_PH_NBR` | `phone` | VARCHAR→STRING | Direct copy |
| `BORR_EMAIL_ADDR` | `email` | VARCHAR→STRING | Direct copy |
| `BORR_CRDT_SCR` | `credit_score` | VARCHAR→INT | Strip commas, cast |
| `BORR_EMP_STAT` | `employment_status` | VARCHAR→STRING | Direct copy |
| `BORR_ANN_INCM` | `annual_income` | VARCHAR→DECIMAL(12,2) | Strip commas, cast |
| `BORR_CRET_DT` | `created_at` | VARCHAR→TIMESTAMP | Parse `MM/DD/YYYY` |
| `BORR_UPDT_DT` | `updated_at` | VARCHAR→TIMESTAMP | Parse `MM/DD/YYYY` |
| `BORR_STAT_CD` | `status` | VARCHAR→STRING | `ACT`→`ACTIVE`, `INA`→`INACTIVE` |
| `BORR_REC_TYP` | *(dropped)* | — | Not needed in modern schema |

### 4.2 CDW_LN_PROD → loan_products

| Legacy Column | Modern Column | Type Change | Transformation |
|---------------|---------------|-------------|----------------|
| `PROD_CD` | `code` | VARCHAR→STRING | Direct copy |
| `PROD_DESC_TXT` | `name` | VARCHAR→STRING | Direct copy |
| `PROD_TYP_CD` | `type` | VARCHAR→STRING | Direct copy |
| `PROD_TERM_MOS` | `term_months` | VARCHAR→INT | Cast |
| `PROD_RT_TYP` | `rate_type` | VARCHAR→STRING | Direct copy |
| `PROD_MIN_AMT` | `min_amount` | VARCHAR→DECIMAL(12,2) | Strip commas, cast |
| `PROD_MAX_AMT` | `max_amount` | VARCHAR→DECIMAL(12,2) | Strip commas, cast |
| `PROD_STAT_CD` | `is_active` | VARCHAR→BOOLEAN | `ACT`→`true`, `INA`→`false` |
| `PROD_EFF_DT` | `effective_date` | VARCHAR→DATE | Parse `MM/DD/YYYY` |
| `PROD_EXP_DT` | `expiration_date` | VARCHAR→DATE | Parse `MM/DD/YYYY` |

### 4.3 CDW_LN_ACCT → loan_accounts

| Legacy Column | Modern Column | Type Change | Transformation |
|---------------|---------------|-------------|----------------|
| `LN_ACCT_NBR` | `account_number` | VARCHAR→STRING | Direct copy |
| `BORR_ID` | `borrower_id` | VARCHAR→BIGINT | FK lookup: `borrowers.id` by `external_id` |
| `BORR_FST_NM` | *(dropped)* | — | Denormalized; use FK |
| `BORR_LST_NM` | *(dropped)* | — | Denormalized; use FK |
| `BORR_SSN_LST4` | *(dropped)* | — | Denormalized; use FK |
| `PROD_CD` | `product_id` | VARCHAR→BIGINT | FK lookup: `loan_products.id` by `code` |
| `LN_ORIG_AMT` | `original_amount` | VARCHAR→DECIMAL(12,2) | Strip commas, cast |
| `LN_CURR_BAL` | `current_balance` | VARCHAR→DECIMAL(12,2) | Strip commas, cast |
| `LN_INT_RT` | `interest_rate` | VARCHAR→DECIMAL(5,3) | Cast |
| `LN_TERM_MOS` | `term_months` | VARCHAR→INT | Cast |
| `LN_PMT_AMT` | `monthly_payment` | VARCHAR→DECIMAL(10,2) | Strip commas, cast |
| `LN_ORIG_DT` | `origination_date` | VARCHAR→DATE | Parse `MM/DD/YYYY` |
| `LN_MAT_DT` | `maturity_date` | VARCHAR→DATE | Parse `MM/DD/YYYY` |
| `LN_1ST_PMT_DT` | `first_payment_date` | VARCHAR→DATE | Parse `MM/DD/YYYY` |
| `LN_NXT_PMT_DT` | `next_payment_date` | VARCHAR→DATE | Parse `MM/DD/YYYY` |
| `LN_STAT_CD` | `status` | VARCHAR→STRING | `ACT`→`ACTIVE`, `CLO`→`CLOSED`, `DFT`→`DEFAULT`, `FRB`→`FORBEARANCE` |
| `LN_DLQ_DAYS` | `delinquency_days` | VARCHAR→INT | Cast |
| `LN_ESCROW_BAL` | `escrow_balance` | VARCHAR→DECIMAL(10,2) | Strip commas, cast |
| `LN_LTV_PCT` | `ltv_percent` | VARCHAR→DECIMAL(5,2) | Cast |
| `PROP_ADDR_LN1` | `property_address` | VARCHAR→STRING | Direct copy |
| `PROP_CTY_NM` | `property_city` | VARCHAR→STRING | Direct copy |
| `PROP_ST_CD` | `property_state` | VARCHAR→STRING | Direct copy |
| `PROP_ZIP_CD` | `property_zip` | VARCHAR→STRING | Direct copy |
| `PROP_TYP_CD` | `property_type` | VARCHAR→STRING | `SFR`→`Single Family`, `CND`→`Condominium`, `MFR`→`Multi-Family`, `TWN`→`Townhouse` |
| `PROP_APRS_VAL` | `appraised_value` | VARCHAR→DECIMAL(12,2) | Strip commas, cast |
| `LN_CRET_DT` | `created_at` | VARCHAR→TIMESTAMP | Parse `MM/DD/YYYY` |
| `LN_UPDT_DT` | `updated_at` | VARCHAR→TIMESTAMP | Parse `MM/DD/YYYY` |

### 4.4 CDW_PMT_HIST → payments

| Legacy Column | Modern Column | Type Change | Transformation |
|---------------|---------------|-------------|----------------|
| `PMT_SEQ_NBR` | `legacy_payment_id` | VARCHAR→STRING | Preserved for audit trail |
| `LN_ACCT_NBR` | `loan_account_id` | VARCHAR→BIGINT | FK lookup: `loan_accounts.id` by `account_number` |
| `PMT_DT` | `payment_date` | VARCHAR→DATE | Parse `MM/DD/YYYY` |
| `PMT_AMT` | `total_amount` | VARCHAR→DECIMAL(10,2) | Strip commas, cast |
| `PMT_PRIN_AMT` | `principal_amount` | VARCHAR→DECIMAL(10,2) | Strip commas, cast |
| `PMT_INT_AMT` | `interest_amount` | VARCHAR→DECIMAL(10,2) | Strip commas, cast |
| `PMT_ESCROW_AMT` | `escrow_amount` | VARCHAR→DECIMAL(10,2) | Strip commas, cast |
| `PMT_LATE_FEE` | `late_fee` | VARCHAR→DECIMAL(10,2) | Strip commas, cast |
| `PMT_TYP_CD` | `type` | VARCHAR→STRING | `REG`→`REGULAR`, `EXT`→`EXTRA`, `PRT`→`PARTIAL`, `PRE`→`PREPAYMENT` |
| `PMT_STAT_CD` | `status` | VARCHAR→STRING | `PST`→`POSTED`, `REV`→`REVERSED`, `NSF`→`NSF`, `PND`→`PENDING` |
| `PMT_RECV_DT` | `received_date` | VARCHAR→DATE | Parse `MM/DD/YYYY` |
| `PMT_PROC_DT` | `processed_date` | VARCHAR→DATE | Parse `MM/DD/YYYY` |
| `PMT_CRET_DT` | `created_at` | VARCHAR→TIMESTAMP | Parse `MM/DD/YYYY` |
| `PMT_UPDT_DT` | `updated_at` | VARCHAR→TIMESTAMP | Parse `MM/DD/YYYY` |
| *(derived)* | `payment_year` | —→INT | `year(payment_date)`, used for partitioning |

---

## 5. Transformation Decisions

### 5.1 Date Handling
All legacy dates are stored as `VARCHAR(10)` in `MM/DD/YYYY` format. PySpark's `to_date()` with format `"MM/dd/yyyy"` converts them to `DateType`. Timestamps (`created_at`, `updated_at`) use `to_timestamp()` with the same format string. Null or unparseable dates are preserved as `NULL` and flagged during quality checks.

### 5.2 Amount / Numeric Parsing
Amounts like `"285,000"` and `"271,432.56"` use comma-separated formatting. The pipeline strips commas with `regexp_replace(col, ",", "")` then casts to the appropriate `DecimalType(precision, scale)`. This handles both whole-number strings and strings with decimal points.

### 5.3 Status Code Expansion
| Domain | Legacy Code | Expanded Value |
|--------|------------|----------------|
| Borrower status | `ACT` | `ACTIVE` |
| Borrower status | `INA` | `INACTIVE` |
| Loan status | `ACT` | `ACTIVE` |
| Loan status | `CLO` | `CLOSED` |
| Loan status | `DFT` | `DEFAULT` |
| Loan status | `FRB` | `FORBEARANCE` |
| Product status | `ACT` | `true` (boolean) |
| Product status | `INA` | `false` (boolean) |
| Payment type | `REG` | `REGULAR` |
| Payment type | `EXT` | `EXTRA` |
| Payment type | `PRT` | `PARTIAL` |
| Payment type | `PRE` | `PREPAYMENT` |
| Payment status | `PST` | `POSTED` |
| Payment status | `REV` | `REVERSED` |
| Payment status | `NSF` | `NSF` |
| Payment status | `PND` | `PENDING` |
| Property type | `SFR` | `Single Family` |
| Property type | `CND` | `Condominium` |
| Property type | `MFR` | `Multi-Family` |
| Property type | `TWN` | `Townhouse` |

Unrecognised codes are preserved as-is (uppercased) and logged as warnings.

### 5.4 Denormalization Removal
`CDW_LN_ACCT` embeds borrower fields (`BORR_FST_NM`, `BORR_LST_NM`, `BORR_SSN_LST4`). These are dropped during ingestion. Borrower data is accessed through the `borrower_id` FK pointing to `loan_warehouse.borrowers`.

### 5.5 Foreign Key Resolution
Legacy tables use string identifiers with no FK constraints:
- `BORR_ID` (e.g., `"B-10001"`) → resolved to `borrowers.id` (BIGINT) via `external_id` lookup
- `PROD_CD` (e.g., `"FXD30"`) → resolved to `loan_products.id` (BIGINT) via `code` lookup
- `LN_ACCT_NBR` (e.g., `"LN-2019-00142"`) → resolved to `loan_accounts.id` (BIGINT) via `account_number` lookup

Unresolved FKs result in `NULL` values and are quarantined with a reason logged.

### 5.6 Identity Column Strategy
Modern tables use `GENERATED ALWAYS AS IDENTITY` (auto-increment) for primary keys. Legacy string identifiers are preserved in dedicated columns (`external_id`, `code`, `account_number`, `legacy_payment_id`) for traceability.

---

## 6. Partitioning Rationale

| Table | Partition Column | Justification |
|-------|-----------------|---------------|
| `loan_accounts` | `status` | Most queries filter by status (`ACTIVE` vs. `CLOSED`). Low cardinality (4 values) keeps partition count manageable. |
| `payments` | `payment_year` | Financial reporting typically queries by time period. Year-level partitioning balances file count with query pruning. |
| `borrowers` | *(none)* | Small table; full scans are efficient. |
| `loan_products` | *(none)* | Very small catalog table (< 100 rows expected). |

---

## 7. Execution Order

The pipeline must execute in dependency order due to FK resolution:

```
Step 1: Create database
        └─ CREATE DATABASE IF NOT EXISTS loan_warehouse

Step 2: Create tables (DDL)
        ├─ databricks/ddl/borrowers.sql
        ├─ databricks/ddl/loan_products.sql
        ├─ databricks/ddl/loan_accounts.sql
        └─ databricks/ddl/payments.sql

Step 3: Place source files
        └─ Upload CSV/Parquet extracts to /mnt/landing/

Step 4: Run ingestion (order matters!)
        ├─ 4a. ingest_borrowers.py       (no FK dependencies)
        ├─ 4b. ingest_loan_products.py   (no FK dependencies)
        ├─ 4c. ingest_loan_accounts.py   (depends on 4a + 4b)
        └─ 4d. ingest_payments.py        (depends on 4c)

Step 5: Run quality checks
        └─ databricks/quality/quality_checks.py

Step 6: Review DATA_QUALITY_REPORT.md
```

Alternatively, run `databricks/ingestion/run_all.py` which orchestrates steps 4a–4d automatically.

---

## 8. Databricks Setup

### 8.1 Cluster Requirements
- Runtime: **Databricks Runtime 14.0+** (includes Delta Lake)
- Node type: Single-node for this dataset size; scale for production
- Libraries: None beyond built-in Spark/Delta

### 8.2 Database Creation
```sql
CREATE DATABASE IF NOT EXISTS loan_warehouse
COMMENT 'Modern loan management data warehouse migrated from CDW'
LOCATION '/mnt/delta/loan_warehouse';
```

### 8.3 Landing Zone
Upload legacy exports as CSV files with headers:
```
/mnt/landing/
  cdw_borr_mstr/     ← borrower CSVs
  cdw_ln_prod/       ← loan product CSVs
  cdw_ln_acct/       ← loan account CSVs
  cdw_pmt_hist/      ← payment history CSVs
```

### 8.4 Running the Pipeline
```bash
# Option A: Full orchestrated run
spark-submit --packages io.delta:delta-spark_2.12:3.1.0 \
    databricks/ingestion/run_all.py

# Option B: Individual steps (useful for debugging)
spark-submit databricks/ingestion/ingest_borrowers.py --source /mnt/landing/cdw_borr_mstr/
spark-submit databricks/ingestion/ingest_loan_products.py --source /mnt/landing/cdw_ln_prod/
spark-submit databricks/ingestion/ingest_loan_accounts.py --source /mnt/landing/cdw_ln_acct/
spark-submit databricks/ingestion/ingest_payments.py --source /mnt/landing/cdw_pmt_hist/

# Quality validation
spark-submit databricks/quality/quality_checks.py \
    --report-path /mnt/reports/DATA_QUALITY_REPORT.md
```

---

## 9. Error Handling & Quarantine

Records that fail validation (null required fields, unresolved FKs, malformed values) are **not silently dropped**. Instead:

1. The record is written to a quarantine Delta table at `/mnt/quarantine/<table_name>/`
2. A `_quarantine_reason` column documents why the record was rejected
3. Row counts are logged and included in the ingestion stats
4. The quality report flags count mismatches between source and target

This ensures full auditability and allows manual review/re-ingestion of problem records.

---

## 10. Rollback

Delta Lake supports time travel, so rolling back is straightforward:

```sql
-- Restore to a previous version
RESTORE TABLE loan_warehouse.borrowers TO VERSION AS OF 0;
RESTORE TABLE loan_warehouse.loan_products TO VERSION AS OF 0;
RESTORE TABLE loan_warehouse.loan_accounts TO VERSION AS OF 0;
RESTORE TABLE loan_warehouse.payments TO VERSION AS OF 0;
```

Or drop and recreate:
```sql
DROP TABLE IF EXISTS loan_warehouse.payments;
DROP TABLE IF EXISTS loan_warehouse.loan_accounts;
DROP TABLE IF EXISTS loan_warehouse.loan_products;
DROP TABLE IF EXISTS loan_warehouse.borrowers;
```

---

## 11. Post-Migration Verification

After the pipeline completes and quality checks pass:

1. **Spot-check** a few records end-to-end (legacy → modern)
2. **Run the Spring Boot app** against the modern schema to verify API parity
3. **Compare golden files** (see `docs/MIGRATION_TASKS.md`, Task 4)
4. **Monitor** Delta table metrics via Databricks SQL dashboards

---

*Generated for the CDW-to-Delta-Lake migration pipeline. See `databricks/` for all implementation artifacts.*
