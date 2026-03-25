-- =============================================================================
-- SQL SERVER: MODERN SCHEMA (Target State)
-- =============================================================================
-- This script creates the normalized modern schema in SQL Server.
-- Key improvements over the legacy Access schema:
--   1. Proper SQL Server data types (DATE, DECIMAL, INT, BIT)
--   2. Clear, readable column names
--   3. Normalized structure (no duplicated borrower data in loans)
--   4. Foreign key constraints for referential integrity
--   5. Proper indexing for query performance
--   6. DATETIME2 timestamps instead of string dates
--   7. CHECK constraints for status/type validation
-- =============================================================================

USE [LoanServiceDB];
GO

-- Create schema for modern tables
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'dbo')
    EXEC('CREATE SCHEMA dbo');
GO

-- =============================================================================
-- borrowers
-- Migrated from: CDW_BORR_MSTR (Access)
-- Changes: Proper types, IDENTITY PK, readable names, CHECK constraints
-- =============================================================================
IF OBJECT_ID('dbo.payments', 'U') IS NOT NULL DROP TABLE dbo.payments;
IF OBJECT_ID('dbo.loan_accounts', 'U') IS NOT NULL DROP TABLE dbo.loan_accounts;
IF OBJECT_ID('dbo.loan_products', 'U') IS NOT NULL DROP TABLE dbo.loan_products;
IF OBJECT_ID('dbo.borrowers', 'U') IS NOT NULL DROP TABLE dbo.borrowers;
GO

CREATE TABLE dbo.borrowers (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    external_id         NVARCHAR(20)    NOT NULL UNIQUE,
    first_name          NVARCHAR(50)    NOT NULL,
    last_name           NVARCHAR(50)    NOT NULL,
    middle_initial      NVARCHAR(1)     NULL,
    ssn_hash            NVARCHAR(100)   NULL,
    date_of_birth       DATE            NULL,
    address_line1       NVARCHAR(100)   NULL,
    address_line2       NVARCHAR(100)   NULL,
    city                NVARCHAR(50)    NULL,
    [state]             NVARCHAR(2)     NULL,
    zip_code            NVARCHAR(10)    NULL,
    phone               NVARCHAR(15)    NULL,
    email               NVARCHAR(100)   NULL,
    credit_score        INT             NULL,
    employment_status   NVARCHAR(20)    NULL,
    annual_income       DECIMAL(12, 2)  NULL,
    [status]            NVARCHAR(10)    DEFAULT 'ACTIVE'
        CONSTRAINT CK_borrowers_status CHECK ([status] IN ('ACTIVE', 'INACTIVE')),
    created_at          DATETIME2       DEFAULT SYSDATETIME(),
    updated_at          DATETIME2       DEFAULT SYSDATETIME()
);
GO

-- =============================================================================
-- loan_products
-- Migrated from: CDW_LN_PROD (Access)
-- Changes: INT term, DECIMAL amounts, BIT for active flag, DATE types
-- =============================================================================
CREATE TABLE dbo.loan_products (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    code                NVARCHAR(10)    NOT NULL UNIQUE,
    [name]              NVARCHAR(200)   NOT NULL,
    [type]              NVARCHAR(5)     NOT NULL
        CONSTRAINT CK_loan_products_type CHECK ([type] IN ('FXD', 'ARM', 'FHA', 'VA')),
    term_months         INT             NOT NULL,
    rate_type           NVARCHAR(10)    NOT NULL
        CONSTRAINT CK_loan_products_rate_type CHECK (rate_type IN ('FIXED', 'VARIABLE')),
    min_amount          DECIMAL(12, 2)  NULL,
    max_amount          DECIMAL(12, 2)  NULL,
    is_active           BIT             DEFAULT 1,
    effective_date      DATE            NULL,
    expiration_date     DATE            NULL
);
GO

-- =============================================================================
-- loan_accounts
-- Migrated from: CDW_LN_ACCT (Access)
-- Changes: Normalized (removed denormalized borrower fields), proper FKs,
--          DECIMAL amounts, DATE types, INT for term/days
-- =============================================================================
CREATE TABLE dbo.loan_accounts (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    account_number      NVARCHAR(20)    NOT NULL UNIQUE,
    borrower_id         BIGINT          NOT NULL,
    product_id          BIGINT          NOT NULL,
    original_amount     DECIMAL(12, 2)  NOT NULL,
    current_balance     DECIMAL(12, 2)  NOT NULL,
    interest_rate       DECIMAL(5, 3)   NOT NULL,
    term_months         INT             NOT NULL,
    monthly_payment     DECIMAL(10, 2)  NOT NULL,
    origination_date    DATE            NOT NULL,
    maturity_date       DATE            NOT NULL,
    first_payment_date  DATE            NULL,
    next_payment_date   DATE            NULL,
    [status]            NVARCHAR(15)    DEFAULT 'ACTIVE'
        CONSTRAINT CK_loan_accounts_status CHECK ([status] IN ('ACTIVE', 'CLOSED', 'DEFAULT', 'FORBEARANCE')),
    delinquency_days    INT             DEFAULT 0,
    escrow_balance      DECIMAL(10, 2)  DEFAULT 0,
    ltv_percent         DECIMAL(5, 2)   NULL,
    property_address    NVARCHAR(100)   NULL,
    property_city       NVARCHAR(50)    NULL,
    property_state      NVARCHAR(2)     NULL,
    property_zip        NVARCHAR(10)    NULL,
    property_type       NVARCHAR(30)    NULL,
    appraised_value     DECIMAL(12, 2)  NULL,
    created_at          DATETIME2       DEFAULT SYSDATETIME(),
    updated_at          DATETIME2       DEFAULT SYSDATETIME(),

    CONSTRAINT FK_loan_accounts_borrower FOREIGN KEY (borrower_id)
        REFERENCES dbo.borrowers(id),
    CONSTRAINT FK_loan_accounts_product FOREIGN KEY (product_id)
        REFERENCES dbo.loan_products(id)
);
GO

-- =============================================================================
-- payments
-- Migrated from: CDW_PMT_HIST (Access)
-- Changes: IDENTITY PK, DECIMAL amounts, DATE types, FK to loan_accounts,
--          expanded type/status values
-- =============================================================================
CREATE TABLE dbo.payments (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    loan_account_id     BIGINT          NOT NULL,
    payment_date        DATE            NOT NULL,
    total_amount        DECIMAL(10, 2)  NOT NULL,
    principal_amount    DECIMAL(10, 2)  NULL,
    interest_amount     DECIMAL(10, 2)  NULL,
    escrow_amount       DECIMAL(10, 2)  NULL,
    late_fee            DECIMAL(10, 2)  DEFAULT 0,
    [type]              NVARCHAR(15)    NOT NULL
        CONSTRAINT CK_payments_type CHECK ([type] IN ('REGULAR', 'EXTRA', 'PARTIAL', 'PREPAYMENT')),
    [status]            NVARCHAR(15)    NOT NULL
        CONSTRAINT CK_payments_status CHECK ([status] IN ('POSTED', 'REVERSED', 'NSF', 'PENDING')),
    received_date       DATE            NULL,
    processed_date      DATE            NULL,
    created_at          DATETIME2       DEFAULT SYSDATETIME(),
    updated_at          DATETIME2       DEFAULT SYSDATETIME(),

    CONSTRAINT FK_payments_loan_account FOREIGN KEY (loan_account_id)
        REFERENCES dbo.loan_accounts(id)
);
GO

-- =============================================================================
-- Indexes for common queries
-- =============================================================================
CREATE NONCLUSTERED INDEX IX_borrowers_email ON dbo.borrowers(email);
CREATE NONCLUSTERED INDEX IX_borrowers_status ON dbo.borrowers([status]);
CREATE NONCLUSTERED INDEX IX_borrowers_external_id ON dbo.borrowers(external_id);

CREATE NONCLUSTERED INDEX IX_loan_accounts_borrower ON dbo.loan_accounts(borrower_id);
CREATE NONCLUSTERED INDEX IX_loan_accounts_status ON dbo.loan_accounts([status]);
CREATE NONCLUSTERED INDEX IX_loan_accounts_product ON dbo.loan_accounts(product_id);

CREATE NONCLUSTERED INDEX IX_payments_loan ON dbo.payments(loan_account_id);
CREATE NONCLUSTERED INDEX IX_payments_date ON dbo.payments(payment_date);
CREATE NONCLUSTERED INDEX IX_payments_status ON dbo.payments([status]);
GO

PRINT 'Modern schema created successfully in SQL Server.';
GO
