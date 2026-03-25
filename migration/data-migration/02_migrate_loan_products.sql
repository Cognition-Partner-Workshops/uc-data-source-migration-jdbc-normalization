-- =============================================================================
-- DATA MIGRATION: CDW_LN_PROD -> loan_products
-- =============================================================================
-- Transforms legacy Access VARCHAR product data to proper SQL Server types.
-- =============================================================================

USE [LoanServiceDB];
GO

PRINT 'Starting loan product migration...';
PRINT '  Source: legacy.CDW_LN_PROD';
PRINT '  Target: dbo.loan_products';
GO

INSERT INTO dbo.loan_products (
    code,
    [name],
    [type],
    term_months,
    rate_type,
    min_amount,
    max_amount,
    is_active,
    effective_date,
    expiration_date
)
SELECT
    src.PROD_CD                                         AS code,
    src.PROD_DESC_TXT                                   AS [name],
    src.PROD_TYP_CD                                     AS [type],

    -- String -> INT conversion for term months
    TRY_CAST(src.PROD_TERM_MOS AS INT)                 AS term_months,

    src.PROD_RT_TYP                                     AS rate_type,

    -- Amount conversion: remove commas, parse to DECIMAL
    TRY_CAST(REPLACE(src.PROD_MIN_AMT, ',', '') AS DECIMAL(12,2))
                                                        AS min_amount,
    TRY_CAST(REPLACE(src.PROD_MAX_AMT, ',', '') AS DECIMAL(12,2))
                                                        AS max_amount,

    -- Status to boolean: ACT -> 1 (true), INA -> 0 (false)
    CASE src.PROD_STAT_CD
        WHEN 'ACT' THEN 1
        WHEN 'INA' THEN 0
        ELSE 1
    END                                                 AS is_active,

    -- Date conversion: MM/DD/YYYY string -> DATE
    TRY_CONVERT(DATE, src.PROD_EFF_DT, 101)            AS effective_date,
    TRY_CONVERT(DATE, src.PROD_EXP_DT, 101)            AS expiration_date

FROM legacy.CDW_LN_PROD src;
GO

-- Verify migration
DECLARE @legacy_count INT, @modern_count INT;
SELECT @legacy_count = COUNT(*) FROM legacy.CDW_LN_PROD;
SELECT @modern_count = COUNT(*) FROM dbo.loan_products;

PRINT 'Loan product migration complete.';
PRINT '  Legacy records: ' + CAST(@legacy_count AS NVARCHAR(10));
PRINT '  Modern records: ' + CAST(@modern_count AS NVARCHAR(10));

IF @legacy_count <> @modern_count
    RAISERROR('WARNING: Row count mismatch in loan product migration!', 16, 1);
GO
