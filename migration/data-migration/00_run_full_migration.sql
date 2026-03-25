-- =============================================================================
-- MASTER MIGRATION SCRIPT: MS Access -> SQL Server
-- =============================================================================
-- Run this script to execute the full migration in the correct order.
-- Prerequisites:
--   1. SQL Server database 'LoanServiceDB' must exist
--   2. Legacy data must be loaded into legacy.* tables
--   3. Modern schema must be created (02_create_modern_schema.sql)
--
-- Execution order:
--   1. Create legacy schema   -> migration/sql-server-schema/01_create_legacy_schema.sql
--   2. Create modern schema   -> migration/sql-server-schema/02_create_modern_schema.sql
--   3. Load legacy data       -> (import from Access or use seed data)
--   4. Migrate borrowers      -> migration/data-migration/01_migrate_borrowers.sql
--   5. Migrate loan products  -> migration/data-migration/02_migrate_loan_products.sql
--   6. Migrate loan accounts  -> migration/data-migration/03_migrate_loan_accounts.sql
--   7. Migrate payments       -> migration/data-migration/04_migrate_payments.sql
--   8. Validate               -> migration/validation/validate_migration.sql
-- =============================================================================

USE [LoanServiceDB];
GO

PRINT '=============================================================================';
PRINT 'MS ACCESS TO SQL SERVER FULL MIGRATION';
PRINT 'Started: ' + CONVERT(NVARCHAR(30), SYSDATETIME(), 121);
PRINT '=============================================================================';
GO

-- Step 1: Migrate borrowers (no dependencies)
PRINT '';
PRINT '--- Step 1/4: Migrating Borrowers ---';
-- Execute: 01_migrate_borrowers.sql
GO

-- Step 2: Migrate loan products (no dependencies)
PRINT '';
PRINT '--- Step 2/4: Migrating Loan Products ---';
-- Execute: 02_migrate_loan_products.sql
GO

-- Step 3: Migrate loan accounts (depends on borrowers + products)
PRINT '';
PRINT '--- Step 3/4: Migrating Loan Accounts ---';
-- Execute: 03_migrate_loan_accounts.sql
GO

-- Step 4: Migrate payments (depends on loan accounts)
PRINT '';
PRINT '--- Step 4/4: Migrating Payments ---';
-- Execute: 04_migrate_payments.sql
GO

PRINT '';
PRINT '=============================================================================';
PRINT 'MIGRATION COMPLETE';
PRINT 'Finished: ' + CONVERT(NVARCHAR(30), SYSDATETIME(), 121);
PRINT '=============================================================================';
PRINT '';
PRINT 'Next steps:';
PRINT '  1. Run validation/validate_migration.sql to verify data integrity';
PRINT '  2. Update application.properties to point to SQL Server';
PRINT '  3. Test all API endpoints';
GO
