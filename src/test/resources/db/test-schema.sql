-- H2-compatible table creation for tests
-- Schemas are created via JDBC INIT parameter

-- Legacy CDW
CREATE TABLE IF NOT EXISTS legacy_cdw.cdw_loan_master (
    record_id           VARCHAR(50) NOT NULL PRIMARY KEY,
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
    record_status       VARCHAR(20)
);

-- ODS
CREATE TABLE IF NOT EXISTS ods.ods_loan_records (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_number         VARCHAR(50) NOT NULL,
    loan_status         VARCHAR(50),
    loan_details_json   CLOB,
    origination_date    DATE,
    maturity_date       DATE,
    outstanding_balance NUMERIC(18,2),
    last_modified       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed           BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS ods.ods_customer_records (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id             VARCHAR(50) NOT NULL,
    customer_name           VARCHAR(200),
    customer_details_json   CLOB,
    credit_score            INTEGER,
    annual_income           NUMERIC(18,2),
    last_modified           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed               BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS ods.ods_collateral_records (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    collateral_id           VARCHAR(50) NOT NULL,
    loan_number             VARCHAR(50),
    collateral_type         VARCHAR(100),
    collateral_details_json CLOB,
    appraised_value         NUMERIC(18,2),
    appraisal_date          DATE,
    last_modified           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed               BOOLEAN DEFAULT FALSE
);

-- Staging
CREATE TABLE IF NOT EXISTS staging.stg_loan (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
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

CREATE TABLE IF NOT EXISTS staging.stg_customer (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
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

CREATE TABLE IF NOT EXISTS staging.stg_collateral (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
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

-- MV Loan
CREATE TABLE IF NOT EXISTS mv_loan.loan_summary (
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

CREATE TABLE IF NOT EXISTS mv_loan.loan_performance (
    loan_number         VARCHAR(50) PRIMARY KEY,
    outstanding_balance NUMERIC(18,2),
    delinquency_days    INTEGER DEFAULT 0,
    risk_rating         VARCHAR(10),
    loan_status         VARCHAR(50),
    maturity_date       DATE,
    last_payment_date   DATE,
    last_refreshed      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- MV Customer
CREATE TABLE IF NOT EXISTS mv_customer.customer_profile (
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

-- MV Collateral
CREATE TABLE IF NOT EXISTS mv_collateral.collateral_registry (
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
