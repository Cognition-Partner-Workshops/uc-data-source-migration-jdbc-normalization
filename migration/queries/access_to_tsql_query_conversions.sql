-- =============================================================================
-- ACCESS QUERY TO T-SQL CONVERSIONS
-- =============================================================================
-- This file documents common Access SQL patterns and their T-SQL equivalents.
-- Each section shows the original Access/Jet SQL and the converted T-SQL.
-- =============================================================================

-- =============================================================================
-- 1. LIST ALL LOANS WITH BORROWER AND PRODUCT INFO
-- =============================================================================
-- ACCESS (Jet SQL):
--   SELECT CDW_LN_ACCT.LN_ACCT_NBR,
--          CDW_LN_ACCT.BORR_FST_NM & " " & CDW_LN_ACCT.BORR_LST_NM AS BorrowerName,
--          CDW_LN_PROD.PROD_DESC_TXT,
--          CDW_LN_ACCT.LN_ORIG_AMT,
--          CDW_LN_ACCT.LN_CURR_BAL,
--          CDW_LN_ACCT.LN_INT_RT,
--          CDW_LN_ACCT.LN_PMT_AMT,
--          IIf(CDW_LN_ACCT.LN_STAT_CD="ACT","Active",
--              IIf(CDW_LN_ACCT.LN_STAT_CD="CLO","Closed","Other")) AS LoanStatus
--   FROM CDW_LN_ACCT
--   LEFT JOIN CDW_LN_PROD ON CDW_LN_ACCT.PROD_CD = CDW_LN_PROD.PROD_CD;
--
-- T-SQL EQUIVALENT:
SELECT
    la.account_number,
    b.first_name + ' ' + b.last_name               AS BorrowerName,
    lp.[name]                                       AS ProductDescription,
    la.original_amount,
    la.current_balance,
    la.interest_rate,
    la.monthly_payment,
    la.[status]                                     AS LoanStatus
FROM dbo.loan_accounts la
INNER JOIN dbo.borrowers b ON b.id = la.borrower_id
INNER JOIN dbo.loan_products lp ON lp.id = la.product_id;
GO

-- =============================================================================
-- 2. FIND BORROWER BY LAST NAME (case-insensitive)
-- =============================================================================
-- ACCESS (Jet SQL):
--   SELECT * FROM CDW_BORR_MSTR
--   WHERE UCase(BORR_LST_NM) = UCase("Mitchell");
--
-- T-SQL EQUIVALENT (SQL Server is case-insensitive by default with
-- Latin1_General_CI_AS collation):
SELECT *
FROM dbo.borrowers
WHERE last_name = 'Mitchell';
-- If explicit case-insensitive needed:
-- WHERE last_name COLLATE Latin1_General_CI_AS = 'Mitchell';
GO

-- =============================================================================
-- 3. LOANS BY STATUS WITH FILTERING
-- =============================================================================
-- ACCESS (Jet SQL):
--   SELECT * FROM CDW_LN_ACCT
--   WHERE LN_STAT_CD = "ACT"
--   AND Val(Nz(LN_DLQ_DAYS, "0")) > 0;
--
-- T-SQL EQUIVALENT:
-- Note: Access Nz() -> T-SQL ISNULL(), Access Val() -> T-SQL TRY_CAST/CAST
SELECT *
FROM dbo.loan_accounts
WHERE [status] = 'ACTIVE'
  AND delinquency_days > 0;
GO

-- =============================================================================
-- 4. PAYMENT HISTORY FOR A LOAN (ordered by date)
-- =============================================================================
-- ACCESS (Jet SQL):
--   SELECT PMT_SEQ_NBR, PMT_DT, PMT_AMT, PMT_PRIN_AMT, PMT_INT_AMT,
--          IIf(PMT_TYP_CD="REG","Regular",
--              IIf(PMT_TYP_CD="EXT","Extra","Other")) AS PaymentType,
--          IIf(PMT_STAT_CD="PST","Posted",
--              IIf(PMT_STAT_CD="REV","Reversed","Other")) AS PaymentStatus
--   FROM CDW_PMT_HIST
--   WHERE LN_ACCT_NBR = "LN-2019-00142"
--   ORDER BY PMT_DT DESC;
--
-- T-SQL EQUIVALENT:
SELECT
    p.id                AS PaymentId,
    p.payment_date,
    p.total_amount,
    p.principal_amount,
    p.interest_amount,
    p.[type]            AS PaymentType,
    p.[status]          AS PaymentStatus
FROM dbo.payments p
INNER JOIN dbo.loan_accounts la ON la.id = p.loan_account_id
WHERE la.account_number = 'LN-2019-00142'
ORDER BY p.payment_date DESC;
GO

-- =============================================================================
-- 5. BORROWER WITH LOAN COUNT AND TOTAL BALANCE
-- =============================================================================
-- ACCESS (Jet SQL):
--   SELECT CDW_BORR_MSTR.BORR_ID,
--          CDW_BORR_MSTR.BORR_FST_NM & " " & CDW_BORR_MSTR.BORR_LST_NM AS FullName,
--          Count(CDW_LN_ACCT.LN_ACCT_NBR) AS LoanCount,
--          Sum(Val(Replace(Nz(CDW_LN_ACCT.LN_CURR_BAL,"0"),",",""))) AS TotalBalance
--   FROM CDW_BORR_MSTR
--   LEFT JOIN CDW_LN_ACCT ON CDW_BORR_MSTR.BORR_ID = CDW_LN_ACCT.BORR_ID
--   GROUP BY CDW_BORR_MSTR.BORR_ID,
--            CDW_BORR_MSTR.BORR_FST_NM & " " & CDW_BORR_MSTR.BORR_LST_NM;
--
-- T-SQL EQUIVALENT:
-- Note: Access Replace() -> T-SQL REPLACE(), Access Val() -> CAST, Access Nz() -> ISNULL()
-- With modern schema, no string parsing needed!
SELECT
    b.external_id,
    b.first_name + ' ' + b.last_name               AS FullName,
    COUNT(la.id)                                    AS LoanCount,
    ISNULL(SUM(la.current_balance), 0)              AS TotalBalance
FROM dbo.borrowers b
LEFT JOIN dbo.loan_accounts la ON la.borrower_id = b.id
GROUP BY b.external_id, b.first_name, b.last_name;
GO

-- =============================================================================
-- 6. DATE RANGE QUERY (payments in a date range)
-- =============================================================================
-- ACCESS (Jet SQL):
--   SELECT * FROM CDW_PMT_HIST
--   WHERE CDate(PMT_DT) BETWEEN #11/01/2025# AND #12/31/2025#;
--
-- T-SQL EQUIVALENT:
-- Note: Access CDate() is not needed — modern schema uses DATE type natively
-- Access date literals #mm/dd/yyyy# -> T-SQL 'YYYY-MM-DD'
SELECT *
FROM dbo.payments
WHERE payment_date BETWEEN '2025-11-01' AND '2025-12-31';
GO

-- =============================================================================
-- 7. TOP N QUERY (largest loans)
-- =============================================================================
-- ACCESS (Jet SQL):
--   SELECT TOP 5 LN_ACCT_NBR,
--          Val(Replace(Nz(LN_ORIG_AMT,"0"),",","")) AS OrigAmount
--   FROM CDW_LN_ACCT
--   ORDER BY Val(Replace(Nz(LN_ORIG_AMT,"0"),",","")) DESC;
--
-- T-SQL EQUIVALENT:
-- Note: Access TOP N -> T-SQL TOP N (same syntax, no string parsing needed)
SELECT TOP 5
    account_number,
    original_amount
FROM dbo.loan_accounts
ORDER BY original_amount DESC;
GO

-- =============================================================================
-- 8. CROSSTAB / PIVOT (payments by month)
-- =============================================================================
-- ACCESS (Jet SQL):
--   TRANSFORM Sum(Val(Replace(Nz(PMT_AMT,"0"),",",""))) AS TotalPaid
--   SELECT LN_ACCT_NBR
--   FROM CDW_PMT_HIST
--   GROUP BY LN_ACCT_NBR
--   PIVOT Format(CDate(PMT_DT),"yyyy-mm");
--
-- T-SQL EQUIVALENT using PIVOT:
SELECT *
FROM (
    SELECT
        la.account_number,
        FORMAT(p.payment_date, 'yyyy-MM')           AS PaymentMonth,
        p.total_amount
    FROM dbo.payments p
    INNER JOIN dbo.loan_accounts la ON la.id = p.loan_account_id
) src
PIVOT (
    SUM(total_amount)
    FOR PaymentMonth IN ([2025-11], [2025-12])
) pvt;
GO

-- =============================================================================
-- 9. PARAMETER QUERY (search borrowers)
-- =============================================================================
-- ACCESS (Jet SQL with parameter):
--   PARAMETERS [Enter Last Name] Text(50);
--   SELECT * FROM CDW_BORR_MSTR
--   WHERE BORR_LST_NM LIKE [Enter Last Name] & "*";
--
-- T-SQL EQUIVALENT (using stored procedure parameter):
-- Note: Access wildcard * -> T-SQL wildcard %
DECLARE @SearchLastName NVARCHAR(50) = 'Mitchell';
SELECT *
FROM dbo.borrowers
WHERE last_name LIKE @SearchLastName + '%';
GO

-- =============================================================================
-- 10. UPDATE QUERY (update loan status)
-- =============================================================================
-- ACCESS (Jet SQL):
--   UPDATE CDW_LN_ACCT SET LN_STAT_CD = "CLO"
--   WHERE LN_ACCT_NBR = "LN-2019-00142";
--
-- T-SQL EQUIVALENT:
-- Note: Also updates the updated_at timestamp automatically
UPDATE dbo.loan_accounts
SET [status] = 'CLOSED',
    updated_at = SYSDATETIME()
WHERE account_number = 'LN-2019-00142';
GO

-- =============================================================================
-- FUNCTION MAPPING REFERENCE: ACCESS -> T-SQL
-- =============================================================================
-- | Access Function  | T-SQL Equivalent          | Notes                       |
-- |------------------|---------------------------|-----------------------------|
-- | IIf(cond,t,f)    | CASE WHEN cond THEN t     | Use CASE WHEN / IIF()       |
-- |                  |   ELSE f END              | (IIF available in SQL 2012+)|
-- | Nz(val, default) | ISNULL(val, default)      | Also: COALESCE()            |
-- | Val(str)         | TRY_CAST(str AS DECIMAL)  | Or CAST/CONVERT             |
-- | CDate(str)       | TRY_CONVERT(DATE, str)    | Specify style code          |
-- | Format(dt, fmt)  | FORMAT(dt, fmt)           | Same in SQL 2012+           |
-- | UCase(str)       | UPPER(str)                |                             |
-- | LCase(str)       | LOWER(str)                |                             |
-- | Left(str, n)     | LEFT(str, n)              | Same                        |
-- | Right(str, n)    | RIGHT(str, n)             | Same                        |
-- | Mid(str, s, n)   | SUBSTRING(str, s, n)      |                             |
-- | Len(str)         | LEN(str)                  | Same                        |
-- | InStr(str, find) | CHARINDEX(find, str)      | Parameter order swapped     |
-- | Replace(s,f,r)   | REPLACE(s, f, r)          | Same                        |
-- | Trim(str)        | LTRIM(RTRIM(str))         | Or TRIM() in SQL 2017+      |
-- | Now()            | SYSDATETIME()             | Or GETDATE()                |
-- | Date()           | CAST(SYSDATETIME() AS     |                             |
-- |                  |   DATE)                   |                             |
-- | IsNull(expr)     | expr IS NULL              | Access IsNull != T-SQL      |
-- |                  |                           | ISNULL — different purpose! |
-- | & (concatenation)| + (concatenation)         | Or CONCAT()                 |
-- | * (wildcard)     | % (wildcard)              | In LIKE patterns            |
-- | ? (wildcard)     | _ (wildcard)              | Single character            |
-- | # (date literal) | 'YYYY-MM-DD' (string)     | T-SQL uses quoted strings   |
-- | True/False       | 1/0                       | BIT type                    |
-- | Yes/No           | 1/0                       | BIT type                    |
-- =============================================================================
