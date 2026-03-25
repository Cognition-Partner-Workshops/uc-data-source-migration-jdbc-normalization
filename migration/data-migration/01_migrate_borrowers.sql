-- =============================================================================
-- DATA MIGRATION: CDW_BORR_MSTR -> borrowers
-- =============================================================================
-- Transforms legacy Access VARCHAR data to proper SQL Server types.
-- Handles: date parsing, amount parsing, status code expansion
-- =============================================================================

USE [LoanServiceDB];
GO

PRINT 'Starting borrower migration...';
PRINT '  Source: legacy.CDW_BORR_MSTR';
PRINT '  Target: dbo.borrowers';
GO

-- Enable identity insert to control IDs if needed
SET IDENTITY_INSERT dbo.borrowers OFF;
GO

INSERT INTO dbo.borrowers (
    external_id,
    first_name,
    last_name,
    middle_initial,
    ssn_hash,
    date_of_birth,
    address_line1,
    address_line2,
    city,
    [state],
    zip_code,
    phone,
    email,
    credit_score,
    employment_status,
    annual_income,
    [status],
    created_at,
    updated_at
)
SELECT
    -- Direct copy fields
    src.BORR_ID                                         AS external_id,
    src.BORR_FST_NM                                     AS first_name,
    src.BORR_LST_NM                                     AS last_name,
    src.BORR_MID_INIT                                   AS middle_initial,
    src.BORR_SSN_ENCR                                   AS ssn_hash,

    -- Date conversion: MM/DD/YYYY string -> DATE
    TRY_CONVERT(DATE, src.BORR_DOB_DT, 101)            AS date_of_birth,

    -- Direct copy address fields
    src.BORR_ADDR_LN1                                   AS address_line1,
    src.BORR_ADDR_LN2                                   AS address_line2,
    src.BORR_CTY_NM                                     AS city,
    src.BORR_ST_CD                                      AS [state],
    src.BORR_ZIP_CD                                     AS zip_code,
    src.BORR_PH_NBR                                     AS phone,
    src.BORR_EMAIL_ADDR                                 AS email,

    -- String -> INT conversion for credit score
    TRY_CAST(src.BORR_CRDT_SCR AS INT)                 AS credit_score,

    src.BORR_EMP_STAT                                   AS employment_status,

    -- Amount conversion: remove commas, parse to DECIMAL
    TRY_CAST(REPLACE(src.BORR_ANN_INCM, ',', '') AS DECIMAL(12,2))
                                                        AS annual_income,

    -- Status code expansion: ACT -> ACTIVE, INA -> INACTIVE
    CASE src.BORR_STAT_CD
        WHEN 'ACT' THEN 'ACTIVE'
        WHEN 'INA' THEN 'INACTIVE'
        ELSE ISNULL(src.BORR_STAT_CD, 'ACTIVE')
    END                                                 AS [status],

    -- Timestamp conversion: MM/DD/YYYY string -> DATETIME2
    ISNULL(TRY_CONVERT(DATETIME2, src.BORR_CRET_DT, 101), SYSDATETIME())
                                                        AS created_at,
    ISNULL(TRY_CONVERT(DATETIME2, src.BORR_UPDT_DT, 101), SYSDATETIME())
                                                        AS updated_at

FROM legacy.CDW_BORR_MSTR src;
GO

-- Verify migration
DECLARE @legacy_count INT, @modern_count INT;
SELECT @legacy_count = COUNT(*) FROM legacy.CDW_BORR_MSTR;
SELECT @modern_count = COUNT(*) FROM dbo.borrowers;

PRINT 'Borrower migration complete.';
PRINT '  Legacy records: ' + CAST(@legacy_count AS NVARCHAR(10));
PRINT '  Modern records: ' + CAST(@modern_count AS NVARCHAR(10));

IF @legacy_count <> @modern_count
    RAISERROR('WARNING: Row count mismatch in borrower migration!', 16, 1);
GO
