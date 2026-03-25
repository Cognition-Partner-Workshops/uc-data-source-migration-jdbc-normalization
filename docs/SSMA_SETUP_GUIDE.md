# SQL Server Migration Assistant (SSMA) Setup Guide

## Overview

This guide walks through setting up and using **SQL Server Migration Assistant (SSMA) for Access** to migrate the legacy MS Access loan management database to SQL Server. SSMA automates schema conversion, data migration, and generates a compatibility assessment report.

## Prerequisites

| Requirement | Details |
|-------------|---------|
| **OS** | Windows 10/11 or Windows Server 2016+ |
| **SQL Server** | SQL Server 2016+ (or Azure SQL Database) |
| **SSMS** | SQL Server Management Studio 18+ |
| **SSMA** | SSMA for Access v8.x+ ([Download](https://learn.microsoft.com/en-us/sql/ssma/access/sql-server-migration-assistant-for-access-accesstosql)) |
| **Access Runtime** | MS Access or Access Database Engine 2016+ |
| **.NET Framework** | 4.7.2 or later |

## Step 1: Install SSMA for Access

1. Download SSMA from the [Microsoft Download Center](https://www.microsoft.com/en-us/download/details.aspx?id=54255).
2. Run the installer (`SSMAforAccess_x.x.msi`).
3. Accept the license agreement and complete the installation.
4. Install the **SSMA Extension Pack** on the target SQL Server instance when prompted.

```
# Verify installation
# SSMA should appear in Start Menu under "Microsoft SQL Server Migration Assistant for Access"
```

## Step 2: Create a New Migration Project

1. Launch **SSMA for Access**.
2. Click **File → New Project**.
3. Configure the project:
   - **Project Name**: `LoanService_AccessToSQLServer`
   - **Project Location**: Choose a working directory
   - **Migrate To**: Select your SQL Server version (e.g., `SQL Server 2019` or `Azure SQL Database`)
4. Click **OK**.

## Step 3: Connect to the Source Access Database

1. Click **Add Databases** in the Access Metadata Explorer panel.
2. Browse to and select your Access database file(s):
   - `LoanService_Legacy.mdb` or `LoanService_Legacy.accdb`
3. SSMA will parse the database and display all objects in the tree:
   ```
   LoanService_Legacy.accdb
   ├── Tables
   │   ├── CDW_BORR_MSTR
   │   ├── CDW_LN_PROD
   │   ├── CDW_LN_ACCT
   │   └── CDW_PMT_HIST
   ├── Queries
   │   ├── qryAllLoans
   │   ├── qryBorrowerLookup
   │   └── qryPaymentHistory
   ├── Forms
   │   ├── frmLoanEntry
   │   └── frmBorrowerSearch
   └── Reports
       └── rptLoanSummary
   ```

## Step 4: Connect to Target SQL Server

1. Click **Connect to SQL Server** in the toolbar.
2. Enter connection details:
   - **Server Name**: `your-server.database.windows.net` (or `localhost\SQLEXPRESS`)
   - **Database**: `LoanServiceDB`
   - **Authentication**: SQL Server Authentication or Windows Authentication
   - **Username/Password**: Your credentials
3. Click **Connect**.

```sql
-- Pre-create the target database if it doesn't exist
CREATE DATABASE LoanServiceDB;
GO
```

## Step 5: Run the Assessment Report

1. In the Access Metadata Explorer, select all tables.
2. Click **Create Report** (or **Assess** button).
3. SSMA generates a compatibility report showing:

### Expected Assessment Results for This Project

| Object | Issues | Severity | Notes |
|--------|--------|----------|-------|
| `CDW_BORR_MSTR` | All VARCHAR columns | Warning | Recommend proper typing |
| `CDW_LN_ACCT` | Denormalized structure | Info | Normalize in target |
| `CDW_LN_PROD` | Date strings in VARCHAR | Warning | Convert to DATE type |
| `CDW_PMT_HIST` | Amount strings with commas | Warning | Convert to DECIMAL |
| All Tables | No foreign keys | Warning | Add FK constraints |
| Queries using `IIf()` | Conversion needed | Info | Use CASE WHEN |
| Queries using `Nz()` | Conversion needed | Info | Use ISNULL() |
| Queries using `Val()` | Conversion needed | Info | Use TRY_CAST() |
| Queries using `CDate()` | Conversion needed | Info | Use TRY_CONVERT() |
| Wildcard `*` in LIKE | Syntax difference | Info | Use `%` in T-SQL |

## Step 6: Configure Type Mappings

Before converting, customize the type mappings for better results:

1. Go to **Tools → Project Settings → Type Mapping**.
2. Override the default mappings:

| Access Type | Default SSMA Mapping | Recommended Override |
|-------------|---------------------|---------------------|
| `Text(20)` [PKs] | `NVARCHAR(20)` | Keep as-is |
| `Text(50)` [names] | `NVARCHAR(50)` | Keep as-is |
| `Text(10)` [dates] | `NVARCHAR(10)` | Override → `DATE` (if date field) |
| `Text(15)` [amounts] | `NVARCHAR(15)` | Override → `DECIMAL(12,2)` (if amount) |
| `Text(5)` [codes] | `NVARCHAR(5)` | Keep as-is |
| `Yes/No` | `BIT` | Keep as-is |
| `AutoNumber` | `INT IDENTITY` | Override → `BIGINT IDENTITY` |
| `Date/Time` | `DATETIME` | Override → `DATETIME2` |

> **Note**: SSMA cannot automatically detect that VARCHAR(10) fields contain dates or that VARCHAR(15) fields contain amounts. The custom migration scripts in `migration/data-migration/` handle these transformations.

## Step 7: Convert Schema

1. In the Access Metadata Explorer, select all objects to migrate.
2. Right-click → **Convert Schema**.
3. SSMA generates the SQL Server DDL. Review the output in the SQL Server Metadata Explorer.
4. Review conversion warnings and resolve any issues.

### Post-Conversion Manual Steps

After SSMA converts the schema, apply these manual improvements:

```sql
-- 1. Add proper data types (SSMA leaves everything as NVARCHAR)
-- Use: migration/sql-server-schema/02_create_modern_schema.sql

-- 2. Add foreign key constraints
-- SSMA cannot infer FK relationships from Access
-- The modern schema script includes all FK definitions

-- 3. Add CHECK constraints for status codes
-- See the modern schema script for constraint definitions

-- 4. Create indexes for query performance
-- See the modern schema script for index definitions
```

## Step 8: Migrate Data

1. In the Access Metadata Explorer, select all tables.
2. Right-click → **Migrate Data**.
3. SSMA transfers all data from Access to SQL Server.
4. Review the data migration report for any errors.

### Post-Data-Migration Transformation

After SSMA loads the raw data, run the transformation scripts:

```bash
# Execute in order:
sqlcmd -S localhost -d LoanServiceDB -i migration/data-migration/01_migrate_borrowers.sql
sqlcmd -S localhost -d LoanServiceDB -i migration/data-migration/02_migrate_loan_products.sql
sqlcmd -S localhost -d LoanServiceDB -i migration/data-migration/03_migrate_loan_accounts.sql
sqlcmd -S localhost -d LoanServiceDB -i migration/data-migration/04_migrate_payments.sql
```

## Step 9: Deploy Stored Procedures

After data migration, deploy the T-SQL stored procedures:

```bash
sqlcmd -S localhost -d LoanServiceDB -i migration/stored-procedures/sp_get_all_loans.sql
sqlcmd -S localhost -d LoanServiceDB -i migration/stored-procedures/sp_get_loan_by_id.sql
sqlcmd -S localhost -d LoanServiceDB -i migration/stored-procedures/sp_get_all_borrowers.sql
sqlcmd -S localhost -d LoanServiceDB -i migration/stored-procedures/sp_get_borrower_with_loans.sql
sqlcmd -S localhost -d LoanServiceDB -i migration/stored-procedures/sp_get_payments_by_loan.sql
```

## Step 10: Validate Migration

```bash
sqlcmd -S localhost -d LoanServiceDB -i migration/validation/validate_migration.sql
```

This runs comprehensive validation checks:
- Row count comparison (legacy vs. modern)
- Data integrity verification per record
- Amount reconciliation
- Date conversion verification
- Foreign key integrity
- Status code expansion verification

## Step 11: Update Application Configuration

Update the Spring Boot application to connect to SQL Server:

```properties
# In application-sqlserver.properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=LoanServiceDB;encrypt=true;trustServerCertificate=true
spring.datasource.driverClassName=com.microsoft.sqlserver.jdbc.SQLServerDriver
spring.datasource.username=sa
spring.datasource.password=YourPassword
spring.jpa.database-platform=org.hibernate.dialect.SQLServerDialect
```

Run with:
```bash
./mvnw spring-boot:run -Dspring.profiles.active=sqlserver
```

## Troubleshooting

### Common SSMA Issues

| Issue | Solution |
|-------|----------|
| "Cannot connect to Access database" | Install Access Database Engine 2016 (32-bit or 64-bit matching SSMA) |
| "Type mapping errors" | Review type mappings in Project Settings |
| "Data truncation during migration" | Increase target column lengths |
| "Unicode conversion errors" | Use NVARCHAR instead of VARCHAR in target |
| "Permission denied on SQL Server" | Grant `db_owner` role to the migration account |
| "SSMA Extension Pack not installed" | Install the extension pack on the target SQL Server |

### Access-Specific Conversion Notes

1. **Access Macros**: Not directly convertible. Business logic should be rewritten as T-SQL stored procedures or application code.
2. **Access Forms**: Not migrated by SSMA. Rebuild using a web framework or reporting tool.
3. **Access Reports**: Consider SQL Server Reporting Services (SSRS) or application-level reporting.
4. **VBA Modules**: Must be manually converted to T-SQL or application code (see `migration/stored-procedures/`).

## Migration Checklist

- [ ] Install SSMA for Access
- [ ] Create target SQL Server database
- [ ] Run SSMA assessment report
- [ ] Configure type mappings
- [ ] Convert schema via SSMA
- [ ] Apply modern schema enhancements (`02_create_modern_schema.sql`)
- [ ] Migrate raw data via SSMA
- [ ] Run data transformation scripts (`01-04_migrate_*.sql`)
- [ ] Deploy stored procedures
- [ ] Run validation queries
- [ ] Update application configuration
- [ ] Test all API endpoints
- [ ] Decommission Access database
