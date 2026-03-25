-- =============================================================================
-- MIGRATION VALIDATION & RECONCILIATION QUERIES
-- =============================================================================
-- Run these queries after migration to verify data integrity.
-- Each query compares legacy source data with modern target data.
-- =============================================================================

USE [LoanServiceDB];
GO

PRINT '=============================================================================';
PRINT 'MIGRATION VALIDATION REPORT';
PRINT 'Run at: ' + CONVERT(NVARCHAR(30), SYSDATETIME(), 121);
PRINT '=============================================================================';
GO

-- =============================================================================
-- 1. ROW COUNT COMPARISON
-- =============================================================================
PRINT '';
PRINT '--- 1. ROW COUNT COMPARISON ---';

SELECT 'CDW_BORR_MSTR -> borrowers' AS Migration,
       (SELECT COUNT(*) FROM legacy.CDW_BORR_MSTR) AS LegacyCount,
       (SELECT COUNT(*) FROM dbo.borrowers) AS ModernCount,
       CASE
           WHEN (SELECT COUNT(*) FROM legacy.CDW_BORR_MSTR) =
                (SELECT COUNT(*) FROM dbo.borrowers)
           THEN 'PASS'
           ELSE 'FAIL'
       END AS [Status]
UNION ALL
SELECT 'CDW_LN_PROD -> loan_products',
       (SELECT COUNT(*) FROM legacy.CDW_LN_PROD),
       (SELECT COUNT(*) FROM dbo.loan_products),
       CASE
           WHEN (SELECT COUNT(*) FROM legacy.CDW_LN_PROD) =
                (SELECT COUNT(*) FROM dbo.loan_products)
           THEN 'PASS' ELSE 'FAIL'
       END
UNION ALL
SELECT 'CDW_LN_ACCT -> loan_accounts',
       (SELECT COUNT(*) FROM legacy.CDW_LN_ACCT),
       (SELECT COUNT(*) FROM dbo.loan_accounts),
       CASE
           WHEN (SELECT COUNT(*) FROM legacy.CDW_LN_ACCT) =
                (SELECT COUNT(*) FROM dbo.loan_accounts)
           THEN 'PASS' ELSE 'FAIL'
       END
UNION ALL
SELECT 'CDW_PMT_HIST -> payments',
       (SELECT COUNT(*) FROM legacy.CDW_PMT_HIST),
       (SELECT COUNT(*) FROM dbo.payments),
       CASE
           WHEN (SELECT COUNT(*) FROM legacy.CDW_PMT_HIST) =
                (SELECT COUNT(*) FROM dbo.payments)
           THEN 'PASS' ELSE 'FAIL'
       END;
GO

-- =============================================================================
-- 2. BORROWER DATA INTEGRITY
-- =============================================================================
PRINT '';
PRINT '--- 2. BORROWER DATA INTEGRITY ---';

SELECT
    'Borrower: ' + src.BORR_ID AS Record,
    CASE
        WHEN tgt.external_id IS NULL THEN 'FAIL - Not migrated'
        WHEN src.BORR_FST_NM <> tgt.first_name THEN 'FAIL - First name mismatch'
        WHEN src.BORR_LST_NM <> tgt.last_name THEN 'FAIL - Last name mismatch'
        WHEN TRY_CAST(src.BORR_CRDT_SCR AS INT) <> tgt.credit_score THEN 'FAIL - Credit score mismatch'
        WHEN TRY_CAST(REPLACE(src.BORR_ANN_INCM, ',', '') AS DECIMAL(12,2)) <> tgt.annual_income
            THEN 'FAIL - Annual income mismatch'
        ELSE 'PASS'
    END AS ValidationResult
FROM legacy.CDW_BORR_MSTR src
LEFT JOIN dbo.borrowers tgt ON tgt.external_id = src.BORR_ID;
GO

-- =============================================================================
-- 3. LOAN ACCOUNT AMOUNT VERIFICATION
-- =============================================================================
PRINT '';
PRINT '--- 3. LOAN ACCOUNT AMOUNT VERIFICATION ---';

SELECT
    'Loan: ' + src.LN_ACCT_NBR AS Record,
    TRY_CAST(REPLACE(src.LN_ORIG_AMT, ',', '') AS DECIMAL(12,2)) AS LegacyOrigAmount,
    tgt.original_amount AS ModernOrigAmount,
    TRY_CAST(REPLACE(src.LN_CURR_BAL, ',', '') AS DECIMAL(12,2)) AS LegacyCurrBal,
    tgt.current_balance AS ModernCurrBal,
    CASE
        WHEN TRY_CAST(REPLACE(src.LN_ORIG_AMT, ',', '') AS DECIMAL(12,2)) = tgt.original_amount
         AND TRY_CAST(REPLACE(src.LN_CURR_BAL, ',', '') AS DECIMAL(12,2)) = tgt.current_balance
        THEN 'PASS'
        ELSE 'FAIL'
    END AS AmountCheck
FROM legacy.CDW_LN_ACCT src
INNER JOIN dbo.loan_accounts tgt ON tgt.account_number = src.LN_ACCT_NBR;
GO

-- =============================================================================
-- 4. PAYMENT TOTAL RECONCILIATION (per loan)
-- =============================================================================
PRINT '';
PRINT '--- 4. PAYMENT TOTAL RECONCILIATION ---';

SELECT
    src.LN_ACCT_NBR AS LoanAccount,
    SUM(TRY_CAST(REPLACE(src.PMT_AMT, ',', '') AS DECIMAL(10,2))) AS LegacyTotal,
    tgt.ModernTotal,
    CASE
        WHEN SUM(TRY_CAST(REPLACE(src.PMT_AMT, ',', '') AS DECIMAL(10,2))) = tgt.ModernTotal
        THEN 'PASS'
        ELSE 'FAIL'
    END AS TotalCheck
FROM legacy.CDW_PMT_HIST src
INNER JOIN (
    SELECT la.account_number, SUM(p.total_amount) AS ModernTotal
    FROM dbo.payments p
    INNER JOIN dbo.loan_accounts la ON la.id = p.loan_account_id
    GROUP BY la.account_number
) tgt ON tgt.account_number = src.LN_ACCT_NBR
GROUP BY src.LN_ACCT_NBR, tgt.ModernTotal;
GO

-- =============================================================================
-- 5. DATE CONVERSION VERIFICATION
-- =============================================================================
PRINT '';
PRINT '--- 5. DATE CONVERSION VERIFICATION ---';

SELECT
    'Borrower DOB: ' + src.BORR_ID AS Record,
    src.BORR_DOB_DT AS LegacyDate,
    CONVERT(NVARCHAR(10), tgt.date_of_birth, 101) AS ModernDateFormatted,
    CASE
        WHEN TRY_CONVERT(DATE, src.BORR_DOB_DT, 101) = tgt.date_of_birth
        THEN 'PASS'
        ELSE 'FAIL'
    END AS DateCheck
FROM legacy.CDW_BORR_MSTR src
INNER JOIN dbo.borrowers tgt ON tgt.external_id = src.BORR_ID;
GO

-- =============================================================================
-- 6. FOREIGN KEY INTEGRITY
-- =============================================================================
PRINT '';
PRINT '--- 6. FOREIGN KEY INTEGRITY ---';

-- Orphaned loan accounts (no matching borrower)
SELECT 'Orphaned Loan Accounts' AS CheckName,
       COUNT(*) AS OrphanCount,
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS [Status]
FROM dbo.loan_accounts la
WHERE NOT EXISTS (SELECT 1 FROM dbo.borrowers b WHERE b.id = la.borrower_id)
UNION ALL
-- Orphaned loan accounts (no matching product)
SELECT 'Orphaned Loan Products',
       COUNT(*),
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM dbo.loan_accounts la
WHERE NOT EXISTS (SELECT 1 FROM dbo.loan_products lp WHERE lp.id = la.product_id)
UNION ALL
-- Orphaned payments (no matching loan account)
SELECT 'Orphaned Payments',
       COUNT(*),
       CASE WHEN COUNT(*) = 0 THEN 'PASS' ELSE 'FAIL' END
FROM dbo.payments p
WHERE NOT EXISTS (SELECT 1 FROM dbo.loan_accounts la WHERE la.id = p.loan_account_id);
GO

-- =============================================================================
-- 7. STATUS CODE EXPANSION VERIFICATION
-- =============================================================================
PRINT '';
PRINT '--- 7. STATUS CODE EXPANSION ---';

SELECT
    'Loan Status: ' + src.LN_ACCT_NBR AS Record,
    src.LN_STAT_CD AS LegacyCode,
    tgt.[status] AS ModernStatus,
    CASE
        WHEN (src.LN_STAT_CD = 'ACT' AND tgt.[status] = 'ACTIVE')
          OR (src.LN_STAT_CD = 'CLO' AND tgt.[status] = 'CLOSED')
          OR (src.LN_STAT_CD = 'DFT' AND tgt.[status] = 'DEFAULT')
          OR (src.LN_STAT_CD = 'FRB' AND tgt.[status] = 'FORBEARANCE')
        THEN 'PASS'
        ELSE 'FAIL'
    END AS StatusCheck
FROM legacy.CDW_LN_ACCT src
INNER JOIN dbo.loan_accounts tgt ON tgt.account_number = src.LN_ACCT_NBR;
GO

PRINT '';
PRINT '=============================================================================';
PRINT 'VALIDATION COMPLETE';
PRINT '=============================================================================';
GO
