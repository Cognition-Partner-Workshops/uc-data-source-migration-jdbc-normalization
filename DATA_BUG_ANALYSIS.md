# Data Bug Analysis: VARCHAR Numeric/Date Sorting

## Problem

The legacy CDW schema stores all columns as `VARCHAR`, including monetary amounts, rates, integer counts, and dates. When JPA derived queries use `ORDER BY` or comparison operators (`>`, `<`) on these columns, the database performs **lexicographic (string) comparison** instead of numeric or chronological comparison.

### Example

| Loan | `LN_CURR_BAL` (VARCHAR) | String Sort Position | Correct Numeric Sort Position |
|------|-------------------------|---------------------|-------------------------------|
| A    | `"5.99"`                | 3rd (highest)       | 5th (lowest)                  |
| B    | `"99.50"`               | 2nd                 | 4th                           |
| C    | `"250.75"`              | 1st (lowest)        | 3rd                           |
| D    | `"1000.00"`             | 4th                 | 2nd                           |
| E    | `"10500.00"`            | 5th                 | 1st (highest)                 |

String sort treats `"9"` > `"1"`, so `"99.50"` appears greater than `"1000.00"`. The same class of bug affects dates stored as `MM/DD/YYYY` strings, where `"02/28/2025"` sorts before `"12/01/2024"` because `"0" < "1"`.

## Root Cause

The legacy data warehouse schema (`schema-legacy.sql`) defines all columns as `VARCHAR`:

```sql
LN_CURR_BAL     VARCHAR(15),    -- current balance as string
LN_ORIG_AMT     VARCHAR(15),    -- original amount as string
LN_INT_RT       VARCHAR(8),     -- interest rate as string "5.250"
LN_DLQ_DAYS     VARCHAR(5),     -- delinquency days
BORR_CRDT_SCR   VARCHAR(5),     -- credit score as string
BORR_ANN_INCM   VARCHAR(15),    -- annual income with commas
PMT_DT          VARCHAR(10),    -- payment date as MM/DD/YYYY
PMT_AMT         VARCHAR(15),    -- payment amount with commas
```

Spring Data JPA derived query methods (e.g. `findByLoanAccountNumberOrderByPaymentDateDesc`) generate SQL that sorts these columns using the column's declared type -- `VARCHAR` -- resulting in lexicographic comparison.

## Affected Queries

### Pre-existing (fixed)

| Repository | Method | Column | Issue |
|---|---|---|---|
| `LegacyPaymentRepository` | `findByLoanAccountNumberOrderByPaymentDateDesc` | `PMT_DT` | MM/DD/YYYY string sort does not match chronological order |

### New queries added with proper CAST

| Repository | Method | Column(s) | Cast |
|---|---|---|---|
| `LegacyLoanAccountRepository` | `findAllOrderByCurrentBalanceDesc/Asc` | `LN_CURR_BAL` | `CAST(REPLACE(col, ',', '') AS DECIMAL(15,2))` |
| `LegacyLoanAccountRepository` | `findByCurrentBalanceGreaterThan` | `LN_CURR_BAL` | `CAST(REPLACE(col, ',', '') AS DECIMAL(15,2))` |
| `LegacyLoanAccountRepository` | `findByCurrentBalanceLessThan` | `LN_CURR_BAL` | `CAST(REPLACE(col, ',', '') AS DECIMAL(15,2))` |
| `LegacyLoanAccountRepository` | `findAllOrderByOriginalAmountDesc` | `LN_ORIG_AMT` | `CAST(REPLACE(col, ',', '') AS DECIMAL(15,2))` |
| `LegacyLoanAccountRepository` | `findAllOrderByInterestRateDesc` | `LN_INT_RT` | `CAST(REPLACE(col, ',', '') AS DECIMAL(8,4))` |
| `LegacyLoanAccountRepository` | `findAllOrderByDelinquencyDaysDesc` | `LN_DLQ_DAYS` | `CAST(col AS INTEGER)` |
| `LegacyPaymentRepository` | `findByLoanAccountNumberOrderByPaymentDateDesc` | `PMT_DT` | `PARSEDATETIME(col, 'MM/dd/yyyy')` |
| `LegacyPaymentRepository` | `findByLoanAccountNumberOrderByTotalAmountDesc` | `PMT_AMT` | `CAST(REPLACE(col, ',', '') AS DECIMAL(15,2))` |
| `LegacyBorrowerRepository` | `findAllOrderByCreditScoreDesc` | `BORR_CRDT_SCR` | `CAST(col AS INTEGER)` |
| `LegacyBorrowerRepository` | `findByAnnualIncomeGreaterThan` | `BORR_ANN_INCM` | `CAST(REPLACE(col, ',', '') AS DECIMAL(15,2))` |

## Fix Applied

Replaced JPA derived query methods with native `@Query` annotations that apply explicit type casts:

- **Monetary amounts** (balances, payments, income): `CAST(REPLACE(column, ',', '') AS DECIMAL(15,2))` strips commas before casting.
- **Rates/percentages**: `CAST(REPLACE(column, ',', '') AS DECIMAL(8,4))` preserves decimal precision.
- **Integer fields** (delinquency days, credit score): `CAST(column AS INTEGER)`.
- **Date fields** (MM/DD/YYYY strings): `PARSEDATETIME(column, 'MM/dd/yyyy')` for proper chronological ordering.

## Test Coverage

Three JUnit test classes added under `src/test/java/.../repository/`:

- **`LegacyLoanAccountRepositoryTest`** (8 tests): Verifies numeric ordering of balances, amounts, rates, delinquency days, and threshold-based filtering. Uses test data where string sort and numeric sort diverge (e.g. balance `"99.50"` vs `"1000.00"`).
- **`LegacyPaymentRepositoryTest`** (4 tests): Verifies chronological date ordering and numeric amount ordering. Uses dates where string sort gives wrong chronological order (e.g. `"02/28/2025"` vs `"12/01/2024"`).
- **`LegacyBorrowerRepositoryTest`** (3 tests): Verifies numeric ordering of credit scores and income-threshold filtering. Uses credit scores with differing digit counts (e.g. `"72"` vs `"780"`).

## Remaining VARCHAR Columns Not Yet Queried

The following VARCHAR columns also contain numeric/date data but have no current sorting or comparison queries. If future features require sorting or filtering on them, the same CAST pattern should be applied:

| Table | Column | Data Type |
|---|---|---|
| `CDW_LN_ACCT` | `LN_PMT_AMT` | Monetary (comma-formatted) |
| `CDW_LN_ACCT` | `LN_ESCROW_BAL` | Monetary (comma-formatted) |
| `CDW_LN_ACCT` | `LN_LTV_PCT` | Percentage decimal |
| `CDW_LN_ACCT` | `PROP_APRS_VAL` | Monetary (comma-formatted) |
| `CDW_LN_ACCT` | `LN_TERM_MOS` | Integer |
| `CDW_LN_ACCT` | `LN_ORIG_DT`, `LN_MAT_DT`, etc. | Date (MM/DD/YYYY) |
| `CDW_PMT_HIST` | `PMT_PRIN_AMT`, `PMT_INT_AMT`, etc. | Monetary |
| `CDW_LN_PROD` | `PROD_MIN_AMT`, `PROD_MAX_AMT` | Monetary (comma-formatted) |
| `CDW_LN_PROD` | `PROD_TERM_MOS` | Integer |
