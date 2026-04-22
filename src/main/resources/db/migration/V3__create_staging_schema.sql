-- =============================================================================
-- V3: Staging Schema
-- Intermediate tables where the batch process places extracted and
-- transformed data before it is loaded into the materialized views.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS staging;

CREATE TABLE staging.stg_loan (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    loan_number         VARCHAR(50) NOT NULL,
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
    branch_code         VARCHAR(20),
    officer_id          VARCHAR(50),
    delinquency_days    INTEGER DEFAULT 0,
    batch_id            BIGINT,
    batch_timestamp     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processing_status   VARCHAR(20) DEFAULT 'STAGED'
);

CREATE TABLE staging.stg_customer (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id         VARCHAR(50) NOT NULL,
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
    batch_id            BIGINT,
    batch_timestamp     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processing_status   VARCHAR(20) DEFAULT 'STAGED'
);

CREATE TABLE staging.stg_collateral (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    collateral_id       VARCHAR(50) NOT NULL,
    loan_number         VARCHAR(50),
    collateral_type     VARCHAR(100),
    description         VARCHAR(500),
    appraised_value     NUMERIC(18,2),
    address             VARCHAR(500),
    appraisal_date      DATE,
    batch_id            BIGINT,
    batch_timestamp     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processing_status   VARCHAR(20) DEFAULT 'STAGED'
);

CREATE INDEX idx_stg_loan_status ON staging.stg_loan(processing_status);
CREATE INDEX idx_stg_customer_status ON staging.stg_customer(processing_status);
CREATE INDEX idx_stg_collateral_status ON staging.stg_collateral(processing_status);
CREATE INDEX idx_stg_loan_batch ON staging.stg_loan(batch_id);
CREATE INDEX idx_stg_customer_batch ON staging.stg_customer(batch_id);
CREATE INDEX idx_stg_collateral_batch ON staging.stg_collateral(batch_id);
