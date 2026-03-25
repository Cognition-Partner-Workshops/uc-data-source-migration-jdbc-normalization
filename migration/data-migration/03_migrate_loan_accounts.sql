-- =============================================================================
-- DATA MIGRATION: CDW_LN_ACCT -> loan_accounts
-- =============================================================================
-- Transforms legacy Access loan account data to normalized SQL Server schema.
-- Key changes:
--   - Resolves borrower_id via FK lookup (external_id -> borrowers.id)
--   - Resolves product_id via FK lookup (code -> loan_products.id)
--   - Drops denormalized borrower fields (name, SSN last 4)
--   - Converts all VARCHAR amounts/dates to proper types
--   - Expands status codes and property type codes
-- =============================================================================

USE [LoanServiceDB];
GO

PRINT 'Starting loan account migration...';
PRINT '  Source: legacy.CDW_LN_ACCT';
PRINT '  Target: dbo.loan_accounts';
GO

INSERT INTO dbo.loan_accounts (
    account_number,
    borrower_id,
    product_id,
    original_amount,
    current_balance,
    interest_rate,
    term_months,
    monthly_payment,
    origination_date,
    maturity_date,
    first_payment_date,
    next_payment_date,
    [status],
    delinquency_days,
    escrow_balance,
    ltv_percent,
    property_address,
    property_city,
    property_state,
    property_zip,
    property_type,
    appraised_value,
    created_at,
    updated_at
)
SELECT
    src.LN_ACCT_NBR                                     AS account_number,

    -- FK resolution: legacy BORR_ID -> modern borrowers.id
    b.id                                                AS borrower_id,

    -- FK resolution: legacy PROD_CD -> modern loan_products.id
    p.id                                                AS product_id,

    -- Amount conversions: remove commas, parse to DECIMAL
    TRY_CAST(REPLACE(src.LN_ORIG_AMT, ',', '') AS DECIMAL(12,2))
                                                        AS original_amount,
    TRY_CAST(REPLACE(src.LN_CURR_BAL, ',', '') AS DECIMAL(12,2))
                                                        AS current_balance,
    TRY_CAST(src.LN_INT_RT AS DECIMAL(5,3))            AS interest_rate,
    TRY_CAST(src.LN_TERM_MOS AS INT)                   AS term_months,
    TRY_CAST(REPLACE(src.LN_PMT_AMT, ',', '') AS DECIMAL(10,2))
                                                        AS monthly_payment,

    -- Date conversions: MM/DD/YYYY -> DATE
    TRY_CONVERT(DATE, src.LN_ORIG_DT, 101)             AS origination_date,
    TRY_CONVERT(DATE, src.LN_MAT_DT, 101)              AS maturity_date,
    TRY_CONVERT(DATE, src.LN_1ST_PMT_DT, 101)          AS first_payment_date,
    TRY_CONVERT(DATE, src.LN_NXT_PMT_DT, 101)          AS next_payment_date,

    -- Status code expansion
    CASE src.LN_STAT_CD
        WHEN 'ACT' THEN 'ACTIVE'
        WHEN 'CLO' THEN 'CLOSED'
        WHEN 'DFT' THEN 'DEFAULT'
        WHEN 'FRB' THEN 'FORBEARANCE'
        ELSE ISNULL(src.LN_STAT_CD, 'ACTIVE')
    END                                                 AS [status],

    ISNULL(TRY_CAST(src.LN_DLQ_DAYS AS INT), 0)        AS delinquency_days,
    TRY_CAST(REPLACE(src.LN_ESCROW_BAL, ',', '') AS DECIMAL(10,2))
                                                        AS escrow_balance,
    TRY_CAST(src.LN_LTV_PCT AS DECIMAL(5,2))           AS ltv_percent,

    -- Property fields (direct copy)
    src.PROP_ADDR_LN1                                   AS property_address,
    src.PROP_CTY_NM                                     AS property_city,
    src.PROP_ST_CD                                      AS property_state,
    src.PROP_ZIP_CD                                     AS property_zip,

    -- Property type expansion
    CASE src.PROP_TYP_CD
        WHEN 'SFR' THEN 'Single Family'
        WHEN 'CND' THEN 'Condominium'
        WHEN 'MFR' THEN 'Multi-Family'
        WHEN 'TWN' THEN 'Townhouse'
        ELSE src.PROP_TYP_CD
    END                                                 AS property_type,

    TRY_CAST(REPLACE(src.PROP_APRS_VAL, ',', '') AS DECIMAL(12,2))
                                                        AS appraised_value,

    -- Timestamps
    ISNULL(TRY_CONVERT(DATETIME2, src.LN_CRET_DT, 101), SYSDATETIME())
                                                        AS created_at,
    ISNULL(TRY_CONVERT(DATETIME2, src.LN_UPDT_DT, 101), SYSDATETIME())
                                                        AS updated_at

FROM legacy.CDW_LN_ACCT src
-- FK lookup joins
INNER JOIN dbo.borrowers b ON b.external_id = src.BORR_ID
INNER JOIN dbo.loan_products p ON p.code = src.PROD_CD;
GO

-- Verify migration
DECLARE @legacy_count INT, @modern_count INT;
SELECT @legacy_count = COUNT(*) FROM legacy.CDW_LN_ACCT;
SELECT @modern_count = COUNT(*) FROM dbo.loan_accounts;

PRINT 'Loan account migration complete.';
PRINT '  Legacy records: ' + CAST(@legacy_count AS NVARCHAR(10));
PRINT '  Modern records: ' + CAST(@modern_count AS NVARCHAR(10));

IF @legacy_count <> @modern_count
    RAISERROR('WARNING: Row count mismatch in loan account migration! Check for missing borrower/product FK references.', 16, 1);
GO
