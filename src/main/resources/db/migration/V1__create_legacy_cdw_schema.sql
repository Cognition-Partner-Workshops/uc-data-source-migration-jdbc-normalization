-- =============================================================================
-- V1: Legacy CDW (Corporate Data Warehouse) Schema
-- Denormalized loan management tables where ALL columns are VARCHAR.
-- This represents the legacy data source that needs to be migrated.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS legacy_cdw;

-- Single denormalized table holding all loan, customer, and collateral data
CREATE TABLE legacy_cdw.cdw_loan_master (
    record_id           VARCHAR(50) NOT NULL,
    loan_number         VARCHAR(50),
    loan_type           VARCHAR(100),
    loan_status         VARCHAR(50),
    loan_amount         VARCHAR(50),
    interest_rate       VARCHAR(20),
    term_months         VARCHAR(10),
    origination_date    VARCHAR(30),
    maturity_date       VARCHAR(30),
    payment_frequency   VARCHAR(30),
    monthly_payment     VARCHAR(50),
    outstanding_balance VARCHAR(50),
    customer_id         VARCHAR(50),
    customer_name       VARCHAR(200),
    customer_ssn        VARCHAR(20),
    customer_dob        VARCHAR(30),
    customer_email      VARCHAR(200),
    customer_phone      VARCHAR(30),
    customer_address    VARCHAR(500),
    customer_city       VARCHAR(100),
    customer_state      VARCHAR(50),
    customer_zip        VARCHAR(20),
    customer_credit_score VARCHAR(10),
    customer_income     VARCHAR(50),
    customer_employer   VARCHAR(200),
    collateral_id       VARCHAR(50),
    collateral_type     VARCHAR(100),
    collateral_description VARCHAR(500),
    collateral_value    VARCHAR(50),
    collateral_address  VARCHAR(500),
    collateral_appraisal_date VARCHAR(30),
    last_payment_date   VARCHAR(30),
    last_payment_amount VARCHAR(50),
    delinquency_days    VARCHAR(10),
    risk_rating         VARCHAR(10),
    branch_code         VARCHAR(20),
    officer_id          VARCHAR(50),
    officer_name        VARCHAR(200),
    created_date        VARCHAR(30),
    modified_date       VARCHAR(30),
    record_status       VARCHAR(20),
    CONSTRAINT pk_cdw_loan_master PRIMARY KEY (record_id)
);
