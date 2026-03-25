-- =============================================================================
-- SQL SERVER: LEGACY SCHEMA (Migrated from MS Access)
-- =============================================================================
-- This script recreates the legacy Access/H2 tables in SQL Server.
-- All columns remain VARCHAR to preserve the legacy data warehouse pattern.
-- This is Step 1: Replicate the Access schema in SQL Server as-is.
-- =============================================================================

USE [LoanServiceDB];
GO

-- Create schema for legacy tables
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'legacy')
    EXEC('CREATE SCHEMA legacy');
GO

-- =============================================================================
-- CDW_BORR_MSTR (Borrower Master)
-- Access: Table with all-text fields, no referential integrity
-- SQL Server: Preserved as-is for initial migration
-- =============================================================================
IF OBJECT_ID('legacy.CDW_BORR_MSTR', 'U') IS NOT NULL
    DROP TABLE legacy.CDW_BORR_MSTR;
GO

CREATE TABLE legacy.CDW_BORR_MSTR (
    BORR_ID         NVARCHAR(20)  NOT NULL PRIMARY KEY,
    BORR_FST_NM     NVARCHAR(50)  NULL,
    BORR_LST_NM     NVARCHAR(50)  NULL,
    BORR_MID_INIT   NVARCHAR(1)   NULL,
    BORR_SSN_ENCR   NVARCHAR(100) NULL,
    BORR_DOB_DT     NVARCHAR(10)  NULL,       -- stored as MM/DD/YYYY
    BORR_ADDR_LN1   NVARCHAR(100) NULL,
    BORR_ADDR_LN2   NVARCHAR(100) NULL,
    BORR_CTY_NM     NVARCHAR(50)  NULL,
    BORR_ST_CD      NVARCHAR(2)   NULL,
    BORR_ZIP_CD     NVARCHAR(10)  NULL,
    BORR_PH_NBR     NVARCHAR(15)  NULL,
    BORR_EMAIL_ADDR NVARCHAR(100) NULL,
    BORR_CRDT_SCR   NVARCHAR(5)   NULL,       -- credit score as string
    BORR_EMP_STAT   NVARCHAR(20)  NULL,
    BORR_ANN_INCM   NVARCHAR(15)  NULL,       -- annual income with commas
    BORR_CRET_DT    NVARCHAR(10)  NULL,       -- created date as MM/DD/YYYY
    BORR_UPDT_DT    NVARCHAR(10)  NULL,       -- updated date as MM/DD/YYYY
    BORR_STAT_CD    NVARCHAR(5)   NULL,
    BORR_REC_TYP    NVARCHAR(10)  NULL
);
GO

-- =============================================================================
-- CDW_LN_PROD (Loan Products)
-- =============================================================================
IF OBJECT_ID('legacy.CDW_LN_PROD', 'U') IS NOT NULL
    DROP TABLE legacy.CDW_LN_PROD;
GO

CREATE TABLE legacy.CDW_LN_PROD (
    PROD_CD         NVARCHAR(10)  NOT NULL PRIMARY KEY,
    PROD_DESC_TXT   NVARCHAR(200) NULL,
    PROD_TYP_CD     NVARCHAR(5)   NULL,       -- FXD, ARM, FHA, VA
    PROD_TERM_MOS   NVARCHAR(5)   NULL,       -- term in months as string
    PROD_RT_TYP     NVARCHAR(10)  NULL,       -- FIXED, VARIABLE
    PROD_MIN_AMT    NVARCHAR(15)  NULL,
    PROD_MAX_AMT    NVARCHAR(15)  NULL,
    PROD_STAT_CD    NVARCHAR(5)   NULL,
    PROD_EFF_DT     NVARCHAR(10)  NULL,
    PROD_EXP_DT     NVARCHAR(10)  NULL
);
GO

-- =============================================================================
-- CDW_LN_ACCT (Loan Accounts - denormalized with borrower data)
-- =============================================================================
IF OBJECT_ID('legacy.CDW_LN_ACCT', 'U') IS NOT NULL
    DROP TABLE legacy.CDW_LN_ACCT;
GO

CREATE TABLE legacy.CDW_LN_ACCT (
    LN_ACCT_NBR     NVARCHAR(20)  NOT NULL PRIMARY KEY,
    BORR_ID         NVARCHAR(20)  NULL,
    -- Denormalized borrower fields (redundant with CDW_BORR_MSTR)
    BORR_FST_NM     NVARCHAR(50)  NULL,
    BORR_LST_NM     NVARCHAR(50)  NULL,
    BORR_SSN_LST4   NVARCHAR(4)   NULL,
    -- Loan fields
    PROD_CD         NVARCHAR(10)  NULL,
    LN_ORIG_AMT     NVARCHAR(15)  NULL,       -- original amount as string
    LN_CURR_BAL     NVARCHAR(15)  NULL,       -- current balance as string
    LN_INT_RT       NVARCHAR(8)   NULL,       -- interest rate as string "5.250"
    LN_TERM_MOS     NVARCHAR(5)   NULL,
    LN_PMT_AMT      NVARCHAR(15)  NULL,       -- monthly payment as string
    LN_ORIG_DT      NVARCHAR(10)  NULL,       -- origination date MM/DD/YYYY
    LN_MAT_DT       NVARCHAR(10)  NULL,       -- maturity date MM/DD/YYYY
    LN_1ST_PMT_DT   NVARCHAR(10)  NULL,
    LN_NXT_PMT_DT   NVARCHAR(10)  NULL,
    LN_STAT_CD      NVARCHAR(5)   NULL,       -- ACT, CLO, DFT, FRB
    LN_DLQ_DAYS     NVARCHAR(5)   NULL,
    LN_ESCROW_BAL   NVARCHAR(15)  NULL,
    LN_LTV_PCT      NVARCHAR(8)   NULL,       -- loan-to-value as string
    PROP_ADDR_LN1   NVARCHAR(100) NULL,
    PROP_CTY_NM     NVARCHAR(50)  NULL,
    PROP_ST_CD      NVARCHAR(2)   NULL,
    PROP_ZIP_CD     NVARCHAR(10)  NULL,
    PROP_TYP_CD     NVARCHAR(10)  NULL,       -- SFR, CND, MFR, TWN
    PROP_APRS_VAL   NVARCHAR(15)  NULL,       -- appraised value as string
    LN_CRET_DT      NVARCHAR(10)  NULL,
    LN_UPDT_DT      NVARCHAR(10)  NULL
);
GO

-- =============================================================================
-- CDW_PMT_HIST (Payment History)
-- =============================================================================
IF OBJECT_ID('legacy.CDW_PMT_HIST', 'U') IS NOT NULL
    DROP TABLE legacy.CDW_PMT_HIST;
GO

CREATE TABLE legacy.CDW_PMT_HIST (
    PMT_SEQ_NBR     NVARCHAR(20)  NOT NULL PRIMARY KEY,
    LN_ACCT_NBR     NVARCHAR(20)  NULL,
    PMT_DT          NVARCHAR(10)  NULL,       -- payment date MM/DD/YYYY
    PMT_AMT         NVARCHAR(15)  NULL,       -- total payment as string
    PMT_PRIN_AMT    NVARCHAR(15)  NULL,       -- principal portion
    PMT_INT_AMT     NVARCHAR(15)  NULL,       -- interest portion
    PMT_ESCROW_AMT  NVARCHAR(15)  NULL,       -- escrow portion
    PMT_LATE_FEE    NVARCHAR(15)  NULL,
    PMT_TYP_CD      NVARCHAR(5)   NULL,       -- REG, EXT, PRT, PRE
    PMT_STAT_CD     NVARCHAR(5)   NULL,       -- PST, REV, NSF, PND
    PMT_RECV_DT     NVARCHAR(10)  NULL,
    PMT_PROC_DT     NVARCHAR(10)  NULL,
    PMT_CRET_DT     NVARCHAR(10)  NULL,
    PMT_UPDT_DT     NVARCHAR(10)  NULL
);
GO

PRINT 'Legacy schema created successfully in SQL Server.';
GO
