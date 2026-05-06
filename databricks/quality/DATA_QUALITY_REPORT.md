# Data Quality Report

**Run:** *(generated at runtime by `quality_checks.py`)*
**Total checks:** 0
**Passed:** 0
**Failed:** 0
**Status:** PENDING

> This file is overwritten each time `quality_checks.py` executes.
> Run the quality framework after ingestion to populate results.

## Expected Checks

### Row Count Reconciliation
| Check | Expected |
|-------|----------|
| borrowers count matches | source=5 |
| loan_products count matches | source=5 |
| loan_accounts count matches | source=5 |
| payments count matches | source=10 |

### Null Checks on Required Fields
- `borrowers`: external_id, first_name, last_name, status
- `loan_products`: code, name, type, term_months, rate_type
- `loan_accounts`: account_number, borrower_id, product_id, original_amount, current_balance, interest_rate, term_months, monthly_payment, origination_date, maturity_date, status
- `payments`: loan_account_id, payment_date, total_amount, type, status

### Referential Integrity
- `loan_accounts.borrower_id` → `borrowers.id`
- `loan_accounts.product_id` → `loan_products.id`
- `payments.loan_account_id` → `loan_accounts.id`

### Business Rule Validation
- Active loans must have balance > 0
- Closed loans must have a maturity date
- Interest rate in [0, 30]%
- Payment amounts > 0
- Credit scores in [300, 850]
- LTV percent in [0, 200]
- Origination date < maturity date

---
*Template generated for the CDW-to-Delta-Lake migration pipeline. See `quality_checks.py` for implementation.*
