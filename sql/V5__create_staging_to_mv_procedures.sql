-- =============================================================================
-- V5: SQL Procedures — Batch job to read staging table data and populate MVs
-- These procedures run in PostgreSQL and move data from staging tables
-- into materialized view tables across different schemas.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Procedure: Populate mv_loan.loan_summary from staging.stg_loan
-- Uses UPSERT (INSERT ... ON CONFLICT ... DO UPDATE) for idempotency
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE staging.load_mv_loan_summary(p_batch_id BIGINT DEFAULT NULL)
LANGUAGE plpgsql
AS $$
DECLARE
    v_rows_affected BIGINT;
BEGIN
    INSERT INTO mv_loan.loan_summary (
        loan_number, loan_type, loan_status, loan_amount, interest_rate,
        term_months, origination_date, maturity_date, payment_frequency,
        monthly_payment, outstanding_balance, customer_id, risk_rating,
        delinquency_days, branch_code, officer_id, last_refreshed
    )
    SELECT
        s.loan_number, s.loan_type, s.loan_status, s.loan_amount, s.interest_rate,
        s.term_months, s.origination_date, s.maturity_date, s.payment_frequency,
        s.monthly_payment, s.outstanding_balance, s.customer_id, s.risk_rating,
        s.delinquency_days, s.branch_code, s.officer_id, CURRENT_TIMESTAMP
    FROM staging.stg_loan s
    WHERE s.processing_status = 'STAGED'
      AND (p_batch_id IS NULL OR s.batch_id = p_batch_id)
    ON CONFLICT (loan_number) DO UPDATE SET
        loan_type           = EXCLUDED.loan_type,
        loan_status         = EXCLUDED.loan_status,
        loan_amount         = EXCLUDED.loan_amount,
        interest_rate       = EXCLUDED.interest_rate,
        term_months         = EXCLUDED.term_months,
        origination_date    = EXCLUDED.origination_date,
        maturity_date       = EXCLUDED.maturity_date,
        payment_frequency   = EXCLUDED.payment_frequency,
        monthly_payment     = EXCLUDED.monthly_payment,
        outstanding_balance = EXCLUDED.outstanding_balance,
        customer_id         = EXCLUDED.customer_id,
        risk_rating         = EXCLUDED.risk_rating,
        delinquency_days    = EXCLUDED.delinquency_days,
        branch_code         = EXCLUDED.branch_code,
        officer_id          = EXCLUDED.officer_id,
        last_refreshed      = CURRENT_TIMESTAMP;

    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;

    UPDATE staging.stg_loan
    SET processing_status = 'MV_LOADED'
    WHERE processing_status = 'STAGED'
      AND (p_batch_id IS NULL OR batch_id = p_batch_id);

    RAISE NOTICE 'load_mv_loan_summary: % rows upserted', v_rows_affected;
END;
$$;

-- -----------------------------------------------------------------------------
-- Procedure: Populate mv_loan.loan_performance from staging.stg_loan
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE staging.load_mv_loan_performance(p_batch_id BIGINT DEFAULT NULL)
LANGUAGE plpgsql
AS $$
DECLARE
    v_rows_affected BIGINT;
BEGIN
    INSERT INTO mv_loan.loan_performance (
        loan_number, outstanding_balance, delinquency_days, risk_rating,
        loan_status, maturity_date, last_refreshed
    )
    SELECT
        s.loan_number, s.outstanding_balance, s.delinquency_days, s.risk_rating,
        s.loan_status, s.maturity_date, CURRENT_TIMESTAMP
    FROM staging.stg_loan s
    WHERE s.processing_status = 'STAGED'
      AND (p_batch_id IS NULL OR s.batch_id = p_batch_id)
    ON CONFLICT (loan_number) DO UPDATE SET
        outstanding_balance = EXCLUDED.outstanding_balance,
        delinquency_days    = EXCLUDED.delinquency_days,
        risk_rating         = EXCLUDED.risk_rating,
        loan_status         = EXCLUDED.loan_status,
        maturity_date       = EXCLUDED.maturity_date,
        last_refreshed      = CURRENT_TIMESTAMP;

    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;
    RAISE NOTICE 'load_mv_loan_performance: % rows upserted', v_rows_affected;
END;
$$;

-- -----------------------------------------------------------------------------
-- Procedure: Populate mv_customer.customer_profile from staging.stg_customer
-- Also computes aggregates (total_loans, total_outstanding) from stg_loan
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE staging.load_mv_customer_profile(p_batch_id BIGINT DEFAULT NULL)
LANGUAGE plpgsql
AS $$
DECLARE
    v_rows_affected BIGINT;
BEGIN
    INSERT INTO mv_customer.customer_profile (
        customer_id, customer_name, date_of_birth, email, phone,
        address_line, city, state, zip_code, credit_score,
        annual_income, employer, total_loans, total_outstanding, last_refreshed
    )
    SELECT
        c.customer_id, c.customer_name, c.date_of_birth, c.email, c.phone,
        c.address_line, c.city, c.state, c.zip_code, c.credit_score,
        c.annual_income, c.employer,
        COALESCE(la.loan_count, 0),
        COALESCE(la.total_balance, 0),
        CURRENT_TIMESTAMP
    FROM staging.stg_customer c
    LEFT JOIN (
        SELECT customer_id,
               COUNT(*) AS loan_count,
               SUM(outstanding_balance) AS total_balance
        FROM staging.stg_loan
        WHERE processing_status IN ('STAGED', 'MV_LOADED')
        GROUP BY customer_id
    ) la ON la.customer_id = c.customer_id
    WHERE c.processing_status = 'STAGED'
      AND (p_batch_id IS NULL OR c.batch_id = p_batch_id)
    ON CONFLICT (customer_id) DO UPDATE SET
        customer_name   = EXCLUDED.customer_name,
        date_of_birth   = EXCLUDED.date_of_birth,
        email           = EXCLUDED.email,
        phone           = EXCLUDED.phone,
        address_line    = EXCLUDED.address_line,
        city            = EXCLUDED.city,
        state           = EXCLUDED.state,
        zip_code        = EXCLUDED.zip_code,
        credit_score    = EXCLUDED.credit_score,
        annual_income   = EXCLUDED.annual_income,
        employer        = EXCLUDED.employer,
        total_loans     = EXCLUDED.total_loans,
        total_outstanding = EXCLUDED.total_outstanding,
        last_refreshed  = CURRENT_TIMESTAMP;

    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;

    UPDATE staging.stg_customer
    SET processing_status = 'MV_LOADED'
    WHERE processing_status = 'STAGED'
      AND (p_batch_id IS NULL OR batch_id = p_batch_id);

    RAISE NOTICE 'load_mv_customer_profile: % rows upserted', v_rows_affected;
END;
$$;

-- -----------------------------------------------------------------------------
-- Procedure: Populate mv_collateral.collateral_registry from staging.stg_collateral
-- Enriches with customer_id by joining through stg_loan
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE staging.load_mv_collateral_registry(p_batch_id BIGINT DEFAULT NULL)
LANGUAGE plpgsql
AS $$
DECLARE
    v_rows_affected BIGINT;
BEGIN
    INSERT INTO mv_collateral.collateral_registry (
        collateral_id, loan_number, collateral_type, description,
        appraised_value, address, appraisal_date, customer_id, last_refreshed
    )
    SELECT
        co.collateral_id, co.loan_number, co.collateral_type, co.description,
        co.appraised_value, co.address, co.appraisal_date,
        l.customer_id,
        CURRENT_TIMESTAMP
    FROM staging.stg_collateral co
    LEFT JOIN staging.stg_loan l ON l.loan_number = co.loan_number
    WHERE co.processing_status = 'STAGED'
      AND (p_batch_id IS NULL OR co.batch_id = p_batch_id)
    ON CONFLICT (collateral_id) DO UPDATE SET
        loan_number     = EXCLUDED.loan_number,
        collateral_type = EXCLUDED.collateral_type,
        description     = EXCLUDED.description,
        appraised_value = EXCLUDED.appraised_value,
        address         = EXCLUDED.address,
        appraisal_date  = EXCLUDED.appraisal_date,
        customer_id     = EXCLUDED.customer_id,
        last_refreshed  = CURRENT_TIMESTAMP;

    GET DIAGNOSTICS v_rows_affected = ROW_COUNT;

    UPDATE staging.stg_collateral
    SET processing_status = 'MV_LOADED'
    WHERE processing_status = 'STAGED'
      AND (p_batch_id IS NULL OR batch_id = p_batch_id);

    RAISE NOTICE 'load_mv_collateral_registry: % rows upserted', v_rows_affected;
END;
$$;

-- -----------------------------------------------------------------------------
-- Master procedure: Orchestrates the full staging-to-MV load
-- Calls each domain-specific procedure in order within a transaction
-- -----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE staging.load_all_materialized_views(p_batch_id BIGINT DEFAULT NULL)
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE NOTICE 'Starting MV load for batch_id=%', p_batch_id;

    CALL staging.load_mv_loan_summary(p_batch_id);
    CALL staging.load_mv_loan_performance(p_batch_id);
    CALL staging.load_mv_customer_profile(p_batch_id);
    CALL staging.load_mv_collateral_registry(p_batch_id);

    RAISE NOTICE 'MV load complete for batch_id=%', p_batch_id;
END;
$$;
