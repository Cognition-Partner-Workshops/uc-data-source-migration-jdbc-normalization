# Data Source Migration — Legacy to Modern

This repository contains a loan management application with a legacy CDW (Corporate Data Warehouse) data source and a modern Spring Boot 3.2 service that extracts ODS (Operational Data Store) data, stages it via Spring Batch, and loads it into Materialized View (MV) tables across separate PostgreSQL schemas for downstream consumption.

## Architecture

### Legacy Loan Service (Existing Scaffold)

```
┌─────────────────────────────┐
│   Loan Service (Spring Boot)│
│                             │
│  Controllers ─► Services    │
│                  │          │
│              Repositories   │
│                  │          │
│         Legacy DataSource   │
│         (CDW-style tables)  │
└─────────────────────────────┘
```

Legacy tables: `CDW_BORR_MSTR`, `CDW_LN_PROD`, `CDW_LN_ACCT`, `CDW_PMT_HIST`
See `data/legacy-schema/` for DDL and `data/mappings/` for column-level mappings.

### Modern ODS-to-MV Pipeline (New)

```
┌─────────────┐     Spring Batch      ┌──────────────┐     SQL INSERT/SELECT     ┌──────────────────┐
│  ODS Tables  │ ──────────────────► │ Staging Tables │ ──────────────────────► │  MV Tables        │
│  (ods.*)     │   JSON extraction   │  (staging.*)   │   status tracking       │  (mv_loan.*,      │
│              │   + transformation  │                │                         │   mv_customer.*,   │
│              │                     │                │                         │   mv_collateral.*) │
└─────────────┘                      └──────────────┘                         └──────────────────┘
```

### Schemas

| Schema | Purpose |
|--------|---------|
| `legacy_cdw` | Legacy CDW denormalized tables (all VARCHAR) |
| `ods` | Operational Data Store — JSON blob + typed columns |
| `staging` | Staging area — properly typed, batch-tracked |
| `mv_loan` | Loan summary and performance views |
| `mv_customer` | Customer profile views |
| `mv_collateral` | Collateral registry views |

## Tech Stack

- Java 17
- Spring Boot 3.2.5
- Spring Batch 5.x
- Spring Data JPA / Hibernate
- PostgreSQL (production) / H2 (tests)
- Flyway (database migrations)
- Jackson (JSON parsing)
- Gradle 8.5 (modern pipeline) / Maven (legacy scaffold)

## Quick Start

### Legacy Loan Service

```bash
./mvnw spring-boot:run
```

Runs on `http://localhost:8080`:
- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Get loan details
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Get borrower with loans
- `GET /api/payments/loan/{loanId}` — Payment history

### Modern ODS Pipeline

```bash
# Prerequisites: JDK 17+, PostgreSQL 14+
createdb datasource_migration

./gradlew build
./gradlew bootRun
```

API Endpoints:
| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/batch/ods-to-staging` | Run Spring Batch job: ODS → Staging |
| `POST` | `/api/batch/staging-to-mv` | Load staging data into all MV tables |
| `POST` | `/api/batch/full-pipeline` | Run complete pipeline: ODS → Staging → MVs |

### Run Tests

```bash
./gradlew test
```

Tests use H2 in-memory database with PostgreSQL compatibility mode — no external database required.

## Project Structure (Modern Pipeline)

```
src/main/java/com/modernbank/datasource/
├── DataSourceMigrationApplication.java
├── batch/
│   ├── OdsStagingBatchConfig.java      # Spring Batch job (3 steps: loan, customer, collateral)
│   └── BatchStepListener.java          # Step execution logging
├── controller/
│   └── BatchJobController.java         # REST API to trigger batch jobs
├── model/
│   ├── ods/                            # ODS entity models (JSON blob + typed columns)
│   └── staging/                        # Staging entity models (properly typed)
├── repository/
│   ├── ods/                            # ODS JPA repositories
│   └── staging/                        # Staging JPA repositories
└── service/
    ├── OdsDataExtractorService.java    # JSON extraction + transformation logic
    └── StagingToMvService.java         # Staging → MV SQL load operations

src/main/resources/
├── application.properties
└── db/migration/
    ├── V1__create_legacy_cdw_schema.sql
    ├── V2__create_ods_schema.sql
    ├── V3__create_staging_schema.sql
    └── V4__create_mv_schemas.sql

sql/
└── V5__create_staging_to_mv_procedures.sql  # PostgreSQL stored procedures
```

## Testing

See [TESTING_REPORT.md](TESTING_REPORT.md) for detailed documentation of all 27 tests and what they validate.

## License

MIT
