-- =============================================================================
-- DATA MIGRATION: CDW_PMT_HIST -> payments
-- =============================================================================
-- Transforms legacy Access payment history to normalized SQL Server schema.
-- Key changes:
--   - Resolves loan_account_id via FK lookup (account_number -> loan_accounts.id)
--   - Converts all VARCHAR amounts to DECIMAL
--   - Converts all VARCHAR dates to DATE
--   - Expands payment type and status codes
-- =============================================================================

USE [LoanServiceDB];
GO

PRINT 'Starting payment history migration...';
PRINT '  Source: legacy.CDW_PMT_HIST';
PRINT '  Target: dbo.payments';
GO

INSERT INTO dbo.payments (
    loan_account_id,
    payment_date,
    total_amount,
    principal_amount,
    interest_amount,
    escrow_amount,
    late_fee,
    [type],
    [status],
    received_date,
    processed_date,
    created_at,
    updated_at
)
SELECT
    -- FK resolution: legacy LN_ACCT_NBR -> modern loan_accounts.id
    la.id                                               AS loan_account_id,

    -- Date conversion
    TRY_CONVERT(DATE, src.PMT_DT, 101)                 AS payment_date,

    -- Amount conversions: remove commas, parse to DECIMAL
    TRY_CAST(REPLACE(src.PMT_AMT, ',', '') AS DECIMAL(10,2))
                                                        AS total_amount,
    TRY_CAST(REPLACE(src.PMT_PRIN_AMT, ',', '') AS DECIMAL(10,2))
                                                        AS principal_amount,
    TRY_CAST(REPLACE(src.PMT_INT_AMT, ',', '') AS DECIMAL(10,2))
                                                        AS interest_amount,
    TRY_CAST(REPLACE(src.PMT_ESCROW_AMT, ',', '') AS DECIMAL(10,2))
                                                        AS escrow_amount,
    ISNULL(TRY_CAST(REPLACE(src.PMT_LATE_FEE, ',', '') AS DECIMAL(10,2)), 0)
                                                        AS late_fee,

    -- Payment type expansion
    CASE src.PMT_TYP_CD
        WHEN 'REG' THEN 'REGULAR'
        WHEN 'EXT' THEN 'EXTRA'
        WHEN 'PRT' THEN 'PARTIAL'
        WHEN 'PRE' THEN 'PREPAYMENT'
        ELSE ISNULL(src.PMT_TYP_CD, 'REGULAR')
    END                                                 AS [type],

    -- Payment status expansion
    CASE src.PMT_STAT_CD
        WHEN 'PST' THEN 'POSTED'
        WHEN 'REV' THEN 'REVERSED'
        WHEN 'NSF' THEN 'NSF'
        WHEN 'PND' THEN 'PENDING'
        ELSE ISNULL(src.PMT_STAT_CD, 'PENDING')
    END                                                 AS [status],

    -- Date conversions
    TRY_CONVERT(DATE, src.PMT_RECV_DT, 101)            AS received_date,
    TRY_CONVERT(DATE, src.PMT_PROC_DT, 101)            AS processed_date,

    -- Timestamps
    ISNULL(TRY_CONVERT(DATETIME2, src.PMT_CRET_DT, 101), SYSDATETIME())
                                                        AS created_at,
    ISNULL(TRY_CONVERT(DATETIME2, src.PMT_UPDT_DT, 101), SYSDATETIME())
                                                        AS updated_at

FROM legacy.CDW_PMT_HIST src
-- FK lookup join
INNER JOIN dbo.loan_accounts la ON la.account_number = src.LN_ACCT_NBR;
GO

-- Verify migration
DECLARE @legacy_count INT, @modern_count INT;
SELECT @legacy_count = COUNT(*) FROM legacy.CDW_PMT_HIST;
SELECT @modern_count = COUNT(*) FROM dbo.payments;

PRINT 'Payment history migration complete.';
PRINT '  Legacy records: ' + CAST(@legacy_count AS NVARCHAR(10));
PRINT '  Modern records: ' + CAST(@modern_count AS NVARCHAR(10));

IF @legacy_count <> @modern_count
    RAISERROR('WARNING: Row count mismatch in payment migration! Check for missing loan account FK references.', 16, 1);
GO
