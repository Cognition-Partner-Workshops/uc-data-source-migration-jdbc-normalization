# Root Cause Analysis — Top 3 Critical Data Anomalies

---

## RCA-1: Payment Component Mismatch (DA-001)

### Anomaly Summary

For loan `LN-2019-00142` (James Mitchell), both December and November 2025 payments have `principal + interest + escrow` that exceed the stated `total_amount` by exactly $400.00:

```
PMT-2025120001: 456.78 + 1,074.69 + 355.55 = 1,887.02  vs  total = 1,487.02  (delta = +400.00)
PMT-2025110001: 454.97 + 1,076.50 + 355.55 = 1,887.02  vs  total = 1,487.02  (delta = +400.00)
```

### Code Path Trace

1. **Data Entry:** `src/main/resources/data-legacy.sql` lines 27-28 insert the payments with pre-formatted string amounts.

2. **Entity Loading:** `LegacyPayment.java` maps all `PMT_*` columns as raw `String` fields — no validation occurs at the JPA layer.

3. **Service Translation:** `LoanService.toPaymentDto()` (lines 134-147) calls `parseLegacyAmount()` on each component individually:
   ```java
   dto.setTotalAmount(parseLegacyAmount(pmt.getTotalAmount()));       // "1,487.02" -> 1487.02
   dto.setPrincipalAmount(parseLegacyAmount(pmt.getPrincipalAmount())); // "456.78" -> 456.78
   dto.setInterestAmount(parseLegacyAmount(pmt.getInterestAmount()));   // "1,074.69" -> 1074.69
   dto.setEscrowAmount(parseLegacyAmount(pmt.getEscrowAmount()));       // "355.55" -> 355.55
   ```

4. **No Cross-Field Validation:** The `toPaymentDto()` method performs no consistency check between the total and its components. Each field is parsed independently and set on the DTO.

5. **API Response:** `LoanController.getPayments()` (line 34) returns the DTO list directly. Consumers receive mathematically inconsistent payment records.

### Root Cause

The legacy CDW source system likely has a bug in its payment posting pipeline: the escrow amount (355.55) appears to be double-counted or applied to a different ledger entry for this specific loan. The consistent $400 delta across two consecutive months suggests a systematic error in the source system's escrow calculation for loan `LN-2019-00142`, not random data corruption.

The application code has **no defensive validation** because `toPaymentDto()` treats each field atomically. The mapping documentation (`data/mappings/column_mappings.md`, lines 82-86) defines each amount column as an independent transformation without specifying a cross-field invariant.

### Runtime Failure Mode

- **Silent data corruption:** The API returns payment objects where the components do not sum to the total. No error is thrown; no log is emitted. Downstream consumers (accounting systems, statements, amortization engines) will silently use incorrect figures.

---

## RCA-2: Unhandled NumberFormatException on Malformed Strings (DA-002)

### Anomaly Summary

All numeric fields are `VARCHAR` in the legacy schema. The service layer parses them with `new BigDecimal(...)` and `Integer.parseInt(...)` without exception handling.

### Code Path Trace

1. **Schema:** `schema-legacy.sql` defines every numeric field as `VARCHAR`:
   - `BORR_CRDT_SCR VARCHAR(5)` (line 27)
   - `BORR_ANN_INCM VARCHAR(15)` (line 29)
   - `LN_ORIG_AMT VARCHAR(15)` (line 60)
   - `LN_INT_RT VARCHAR(8)` (line 62)
   - `PMT_AMT VARCHAR(15)` (line 88)

2. **Parsing Methods in `LoanService.java`:**

   ```java
   // Line 152-155: parseLegacyAmount
   private BigDecimal parseLegacyAmount(String amount) {
       if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
       return new BigDecimal(amount.replace(",", ""));  // throws NumberFormatException
   }

   // Line 157-160: parseLegacyDecimal
   private BigDecimal parseLegacyDecimal(String value) {
       if (value == null || value.isBlank()) return BigDecimal.ZERO;
       return new BigDecimal(value.trim());  // throws NumberFormatException
   }

   // Line 162-165: parseLegacyInteger
   private Integer parseLegacyInteger(String value) {
       if (value == null || value.isBlank()) return null;
       return Integer.parseInt(value.trim());  // throws NumberFormatException
   }
   ```

3. **Call Sites:**
   - `toLoanSummary()` line 108: `parseLegacyAmount(acct.getOriginalAmount())` — if `LN_ORIG_AMT` contains `"N/A"` or `"$285,000"`, throws.
   - `toLoanSummary()` line 110: `parseLegacyDecimal(acct.getInterestRate())` — if `LN_INT_RT` contains `"TBD"`, throws.
   - `toBorrowerDto()` line 129: `parseLegacyInteger(borrower.getCreditScore())` — if `BORR_CRDT_SCR` contains `"N/A"` or `"---"`, throws.
   - `toPaymentDto()` lines 139-143: all five amount fields parsed — any single malformed value crashes the entire request.

4. **Error Propagation:** `NumberFormatException` is unchecked. Spring Boot's default error handler returns HTTP 500 with a stack trace (in dev) or a generic error (in prod).

### Root Cause

The parsing methods only guard against `null` and blank strings. They do not catch `NumberFormatException`. The mapping documentation (`column_mappings.md`) specifies transformations like "Remove commas, parse -> decimal" but does not mention error handling for non-conforming values.

In legacy CDW systems, it is common for VARCHAR fields to contain placeholder values (`"N/A"`, `"PENDING"`, `"---"`, `"0.00*"`) inserted by upstream batch jobs that failed mid-execution. The current code assumes all non-null, non-blank values are well-formed numbers.

### Runtime Failure Mode

- **Cascading API failure:** The `getAllLoans()` method streams over all accounts. A single malformed record causes `NumberFormatException` inside the `.map(acct -> toLoanSummary(...))` lambda, which propagates up and returns HTTP 500 for the entire endpoint. This means one bad record takes down the list endpoint for all users.

---

## RCA-3: No Foreign Key Validation — Orphaned Records Cause Silent Data Loss (DA-003)

### Anomaly Summary

The legacy schema has zero foreign key constraints. `CDW_LN_ACCT.BORR_ID`, `CDW_LN_ACCT.PROD_CD`, and `CDW_PMT_HIST.LN_ACCT_NBR` are unconstrained VARCHAR fields.

### Code Path Trace

1. **Schema:** `schema-legacy.sql` lines 1-9 explicitly document "No foreign key constraints."

2. **Product Lookup in `getAllLoans()`** (`LoanService.java` lines 48-55):
   ```java
   Map<String, LegacyLoanProduct> products = loanProductRepository.findAll()
       .stream()
       .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));

   return loanAccountRepository.findAll().stream()
       .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
       .collect(Collectors.toList());
   ```
   If `acct.getProductCode()` is `"INVALID"`, then `products.get(...)` returns `null`.

3. **Null Product Handling in `toLoanSummary()`** (line 107):
   ```java
   dto.setProductDescription(product != null ? product.getDescription() : acct.getProductCode());
   ```
   When `product` is `null`, the raw product code (e.g., `"INVALID"`) is silently used as the description. No warning is logged.

4. **Borrower Lookup in `getBorrowerById()`** (lines 81-84):
   ```java
   List<LoanSummaryDto> loans = loanAccountRepository.findByBorrowerId(borrowerId)
       .stream()
       .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
       .collect(Collectors.toList());
   ```
   If a loan has `BORR_ID = 'B-99999'` (orphaned), it will never appear in any borrower's loan list. Conversely, if a borrower exists but has no loans, `findByBorrowerId` returns an empty list — indistinguishable from the orphaned case.

5. **Payment Lookup** (line 91):
   ```java
   paymentRepository.findByLoanAccountNumberOrderByPaymentDateDesc(loanAccountNumber)
   ```
   Payments with an invalid `LN_ACCT_NBR` are invisible — they exist in the database but can never be retrieved through the API since no loan account matches them.

### Root Cause

The legacy CDW design intentionally omits foreign keys for bulk-load performance (common in data warehouse architectures). The application's service layer was written assuming referential integrity holds at the data level, so it does not validate relationships.

The mapping documentation (`column_mappings.md` lines 48, 52, 80) documents FK resolution as a migration concern but does not flag it as a current validation requirement.

### Runtime Failure Mode

- **Silent data omission:** Orphaned loans are invisible in borrower responses. Orphaned payments are unretrievable. No error is thrown; no log is emitted. Audit trails become incomplete.
- **Incorrect product display:** Loans referencing non-existent products display raw codes like `"INVALID"` instead of product names, which confuses API consumers.
