# Data Source Migration: Legacy to Modern

A loan management application with an **Angular** frontend and **Express.js** backend that reads from a legacy data warehouse schema. The workshop challenge is to migrate the data source to a modern normalized schema while keeping the application functional.

## Overview

This app manages loan data: borrowers, loan products, loan accounts, and payment history. It currently reads from legacy tables with denormalized structures, cryptic column names, and outdated patterns. The goal is to rewire it to use a normalized modern schema with clear naming conventions.

## Architecture

```
┌──────────────────────────────────────────────────┐
│  Angular Frontend (client/)                      │
│  Angular 17 · Angular Material · Standalone      │
│  Pages: /loans, /loans/:id,                      │
│         /borrowers, /borrowers/:id               │
└──────────────┬───────────────────────────────────┘
               │ HTTP (port 4200 → proxy → 3000)
┌──────────────▼───────────────────────────────────┐
│  Express.js Backend (server/)                    │
│  TypeScript · CORS · sql.js (in-memory SQLite)   │
│                                                  │
│  Routes ─► Services ─► sql.js Database           │
│                                                  │
│         Legacy DataSource                        │
│         (CDW_* legacy schema)  ← YOU ARE HERE    │
│                                                  │
│         Modern DataSource                        │
│         (normalized schema)    ← MIGRATE TO HERE │
└──────────────────────────────────────────────────┘
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
# Install dependencies
cd server && npm install && cd ..
cd client && npm install && cd ..

# Run backend (port 3000)
cd server && npm run dev

# Run frontend (port 4200) — in another terminal
cd client && npx ng serve --proxy-config proxy.conf.json
```

The frontend runs on `http://localhost:4200` and proxies API requests to the backend on port 3000.

### API Endpoints (backend)

- `GET /api/loans` — List all loans
- `GET /api/loans/{id}` — Get loan details
- `GET /api/loans/{loanId}/payments` — Payment history for a loan
- `GET /api/borrowers` — List borrowers
- `GET /api/borrowers/{id}` — Get borrower with loans

## Tech Stack

- **Frontend:** Angular 17, Angular Material, TypeScript, SCSS
- **Backend:** Express.js, TypeScript, sql.js (SQLite in WASM)
- **Database:** In-memory SQLite (loaded from legacy SQL seed files)
- **Build:** npm workspaces

## Project Structure

```
├── client/             # Angular 17 frontend
│   └── src/app/
│       ├── models/     # TypeScript interfaces
│       ├── services/   # HTTP services
│       └── pages/      # Page components (loan-list, loan-detail, etc.)
├── server/             # Express.js backend
│   ├── db/             # SQL schema & seed files
│   └── src/
│       ├── models/     # Legacy & DTO interfaces
│       ├── services/   # Business logic (legacy translation)
│       ├── routes/     # Express route handlers
│       └── utils/      # Legacy data transformers
├── data/               # Workshop reference files (schemas, mappings)
└── docs/               # Migration task documentation
```

## License

MIT
