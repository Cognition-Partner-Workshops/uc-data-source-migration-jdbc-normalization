-- =============================================================================
-- V2: ODS (Operational Data Store) Schema
-- Contains operational data in two forms:
--   1. JSON blob columns (loan_details_json, customer_details_json)
--   2. Normal typed columns
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS ods;

CREATE TABLE ods.ods_loan_records (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    loan_number         VARCHAR(50) NOT NULL,
    loan_status         VARCHAR(50),
    loan_details_json   TEXT,
    origination_date    DATE,
    maturity_date       DATE,
    outstanding_balance NUMERIC(18,2),
    last_modified       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed           BOOLEAN DEFAULT FALSE
);

CREATE TABLE ods.ods_customer_records (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id             VARCHAR(50) NOT NULL,
    customer_name           VARCHAR(200),
    customer_details_json   TEXT,
    credit_score            INTEGER,
    annual_income           NUMERIC(18,2),
    last_modified           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed               BOOLEAN DEFAULT FALSE
);

CREATE TABLE ods.ods_collateral_records (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    collateral_id           VARCHAR(50) NOT NULL,
    loan_number             VARCHAR(50),
    collateral_type         VARCHAR(100),
    collateral_details_json TEXT,
    appraised_value         NUMERIC(18,2),
    appraisal_date          DATE,
    last_modified           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed               BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_ods_loan_processed ON ods.ods_loan_records(processed);
CREATE INDEX idx_ods_customer_processed ON ods.ods_customer_records(processed);
CREATE INDEX idx_ods_collateral_processed ON ods.ods_collateral_records(processed);
