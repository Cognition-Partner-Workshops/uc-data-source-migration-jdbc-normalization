-- =============================================================================
-- V4: Materialized View (MV) Schemas
-- Target tables in separate schemas that expose client data to
-- different downstream systems. Each schema serves a specific domain.
-- =============================================================================

-- Schema for loan-focused downstream consumers
CREATE SCHEMA IF NOT EXISTS mv_loan;

CREATE TABLE mv_loan.loan_summary (
    loan_number         VARCHAR(50) PRIMARY KEY,
    loan_type           VARCHAR(100),
    loan_status         VARCHAR(50),
    loan_amount         NUMERIC(18,2),
    interest_rate       NUMERIC(8,4),
    term_months         INTEGER,
    origination_date    DATE,
    maturity_date       DATE,
    payment_frequency   VARCHAR(30),
    monthly_payment     NUMERIC(18,2),
    outstanding_balance NUMERIC(18,2),
    customer_id         VARCHAR(50),
    risk_rating         VARCHAR(10),
    delinquency_days    INTEGER DEFAULT 0,
    branch_code         VARCHAR(20),
    officer_id          VARCHAR(50),
    last_refreshed      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE mv_loan.loan_performance (
    loan_number         VARCHAR(50) PRIMARY KEY,
    outstanding_balance NUMERIC(18,2),
    delinquency_days    INTEGER DEFAULT 0,
    risk_rating         VARCHAR(10),
    loan_status         VARCHAR(50),
    maturity_date       DATE,
    last_payment_date   DATE,
    last_refreshed      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Schema for customer-focused downstream consumers
CREATE SCHEMA IF NOT EXISTS mv_customer;

CREATE TABLE mv_customer.customer_profile (
    customer_id         VARCHAR(50) PRIMARY KEY,
    customer_name       VARCHAR(200),
    date_of_birth       DATE,
    email               VARCHAR(200),
    phone               VARCHAR(30),
    address_line        VARCHAR(500),
    city                VARCHAR(100),
    state               VARCHAR(50),
    zip_code            VARCHAR(20),
    credit_score        INTEGER,
    annual_income       NUMERIC(18,2),
    employer            VARCHAR(200),
    total_loans         INTEGER DEFAULT 0,
    total_outstanding   NUMERIC(18,2) DEFAULT 0,
    last_refreshed      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Schema for collateral-focused downstream consumers
CREATE SCHEMA IF NOT EXISTS mv_collateral;

CREATE TABLE mv_collateral.collateral_registry (
    collateral_id       VARCHAR(50) PRIMARY KEY,
    loan_number         VARCHAR(50),
    collateral_type     VARCHAR(100),
    description         VARCHAR(500),
    appraised_value     NUMERIC(18,2),
    address             VARCHAR(500),
    appraisal_date      DATE,
    customer_id         VARCHAR(50),
    last_refreshed      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_mv_loan_customer ON mv_loan.loan_summary(customer_id);
CREATE INDEX idx_mv_loan_perf_status ON mv_loan.loan_performance(loan_status);
CREATE INDEX idx_mv_collateral_loan ON mv_collateral.collateral_registry(loan_number);
