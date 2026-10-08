# Data Source Migration: Legacy to Modern

A small Spring Boot loan management application that currently connects to a **legacy data warehouse** (simulated via H2 with legacy-style schemas). The workshop challenge is to migrate the data source to a **modern schema** while keeping the application functional.

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and payment history. It currently reads from legacy tables with denormalized structures, cryptic column names, and outdated patterns. The goal is to rewire it to use a normalized modern schema with clear naming conventions.

## Architecture

```
┌─────────────────────────────┐
│   Loan Service (Spring Boot)│
│                             │
│  Controllers ─► Services    │
│                  │          │
│              Repositories   │
│                  │          │
│         Legacy DataSource   │  ← YOU ARE HERE
│         (H2 / legacy schema)│
│                             │
│         Modern DataSource   │  ← MIGRATE TO HERE
│         (H2 / modern schema)│
└─────────────────────────────┘
```

## Current State (Legacy)

The app connects to legacy tables:
- `CDW_BORR_MSTR` — Borrower master (denormalized, cryptic columns)
- `CDW_LN_PROD` — Loan products
- `CDW_LN_ACCT` — Loan accounts (wide table with embedded borrower data)
- `CDW_PMT_HIST` — Payment history

See `data/legacy-schema/` for full DDL and `data/mappings/` for column-level mappings.

## Target State (Modern)

Migrate to normalized tables:
- `borrowers` — Clean borrower records
- `loan_products` — Product catalog
- `loan_accounts` — Normalized loan accounts with foreign keys
- `payments` — Payment records

See `data/modern-schema/` for target DDL.

## Quick Start

```bash
./mvnw spring-boot:run
```

The app runs on `http://localhost:8080` with endpoints:
- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Get loan details
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Get borrower with loans
- `GET /api/loans/{id}/payments` — Paginated, filterable payment history for a loan

### Payment history API

`GET /api/loans/{id}/payments`

| Query param | Description | Default |
|---|---|---|
| `startDate` | Earliest payment date, inclusive (ISO `yyyy-MM-dd`) | — |
| `endDate` | Latest payment date, inclusive (ISO `yyyy-MM-dd`) | — |
| `type` | Comma-separated payment types: `REGULAR`, `EXTRA`, `PARTIAL`, `PREPAYMENT` (legacy codes `REG`/`EXT`/`PRT`/`PRE` and labels are also accepted, case-insensitive) | all |
| `page` | Zero-based page index | `0` |
| `size` | Page size (1–100) | `20` |

Results are ordered newest first. Example:

```
GET /api/loans/LN-2019-00142/payments?startDate=2025-11-01&endDate=2025-12-31&type=REGULAR&page=0&size=10
```

```json
{
  "content": [{ "paymentId": "PMT-2025120001", "paymentDate": "12/15/2025", "totalAmount": 1487.02, "type": "Regular", "status": "Posted", ... }],
  "page": 0, "size": 10, "totalElements": 2, "totalPages": 1, "first": true, "last": true
}
```

Errors use a consistent body (`timestamp`, `status`, `error`, `message`, `path`):
- `404` — loan does not exist
- `400` — invalid date format, `startDate` after `endDate`, unknown payment type, or out-of-range `page`/`size`

## Tech Stack

- Java 17
- Spring Boot 3.2
- Spring Data JPA
- H2 (in-memory, simulating legacy DW)
- Maven

## License

MIT
