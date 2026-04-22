package com.modernbank.datasource.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invokes the PostgreSQL stored procedures that load data from staging tables
 * into the materialized view tables across different schemas.
 * In the H2 test environment, executes equivalent SQL directly.
 */
@Service
public class StagingToMvService {

    private static final Logger log = LoggerFactory.getLogger(StagingToMvService.class);

    private final JdbcTemplate jdbcTemplate;

    public StagingToMvService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void loadLoanSummary(Long batchId) {
        log.info("Loading loan summary MV for batchId={}", batchId);
        int rows = insertLoanSummary(batchId);
        log.info("Loaded {} rows into mv_loan.loan_summary", rows);
        updateStagingLoanStatus(batchId);
    }

    private int insertLoanSummary(Long batchId) {
        String sql = """
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
              AND (? IS NULL OR s.batch_id = ?)
            """;
        return jdbcTemplate.update(sql, batchId, batchId);
    }

    @Transactional
    public void loadLoanPerformance(Long batchId) {
        log.info("Loading loan performance MV for batchId={}", batchId);
        int rows = insertLoanPerformance(batchId);
        log.info("Loaded {} rows into mv_loan.loan_performance", rows);
    }

    private int insertLoanPerformance(Long batchId) {
        String sql = """
            INSERT INTO mv_loan.loan_performance (
                loan_number, outstanding_balance, delinquency_days, risk_rating,
                loan_status, maturity_date, last_refreshed
            )
            SELECT
                s.loan_number, s.outstanding_balance, s.delinquency_days, s.risk_rating,
                s.loan_status, s.maturity_date, CURRENT_TIMESTAMP
            FROM staging.stg_loan s
            WHERE s.processing_status = 'STAGED'
              AND (? IS NULL OR s.batch_id = ?)
            """;
        return jdbcTemplate.update(sql, batchId, batchId);
    }

    @Transactional
    public void loadCustomerProfile(Long batchId) {
        log.info("Loading customer profile MV for batchId={}", batchId);

        String sql = """
            INSERT INTO mv_customer.customer_profile (
                customer_id, customer_name, date_of_birth, email, phone,
                address_line, city, state, zip_code, credit_score,
                annual_income, employer, last_refreshed
            )
            SELECT
                c.customer_id, c.customer_name, c.date_of_birth, c.email, c.phone,
                c.address_line, c.city, c.state, c.zip_code, c.credit_score,
                c.annual_income, c.employer, CURRENT_TIMESTAMP
            FROM staging.stg_customer c
            WHERE c.processing_status = 'STAGED'
              AND (? IS NULL OR c.batch_id = ?)
            """;

        int rows = jdbcTemplate.update(sql, batchId, batchId);
        log.info("Loaded {} rows into mv_customer.customer_profile", rows);

        updateStagingCustomerStatus(batchId);
    }

    @Transactional
    public void loadCollateralRegistry(Long batchId) {
        log.info("Loading collateral registry MV for batchId={}", batchId);

        String sql = """
            INSERT INTO mv_collateral.collateral_registry (
                collateral_id, loan_number, collateral_type, description,
                appraised_value, address, appraisal_date, last_refreshed
            )
            SELECT
                co.collateral_id, co.loan_number, co.collateral_type, co.description,
                co.appraised_value, co.address, co.appraisal_date, CURRENT_TIMESTAMP
            FROM staging.stg_collateral co
            WHERE co.processing_status = 'STAGED'
              AND (? IS NULL OR co.batch_id = ?)
            """;

        int rows = jdbcTemplate.update(sql, batchId, batchId);
        log.info("Loaded {} rows into mv_collateral.collateral_registry", rows);

        updateStagingCollateralStatus(batchId);
    }

    @Transactional
    public void loadAllMaterializedViews(Long batchId) {
        log.info("Starting full MV load for batchId={}", batchId);

        int summaryRows = insertLoanSummary(batchId);
        log.info("Loaded {} rows into mv_loan.loan_summary", summaryRows);

        int perfRows = insertLoanPerformance(batchId);
        log.info("Loaded {} rows into mv_loan.loan_performance", perfRows);

        updateStagingLoanStatus(batchId);

        loadCustomerProfile(batchId);
        loadCollateralRegistry(batchId);
        log.info("Completed full MV load for batchId={}", batchId);
    }

    private void updateStagingLoanStatus(Long batchId) {
        String sql = """
            UPDATE staging.stg_loan
            SET processing_status = 'MV_LOADED'
            WHERE processing_status = 'STAGED'
              AND (? IS NULL OR batch_id = ?)
            """;
        jdbcTemplate.update(sql, batchId, batchId);
    }

    private void updateStagingCustomerStatus(Long batchId) {
        String sql = """
            UPDATE staging.stg_customer
            SET processing_status = 'MV_LOADED'
            WHERE processing_status = 'STAGED'
              AND (? IS NULL OR batch_id = ?)
            """;
        jdbcTemplate.update(sql, batchId, batchId);
    }

    private void updateStagingCollateralStatus(Long batchId) {
        String sql = """
            UPDATE staging.stg_collateral
            SET processing_status = 'MV_LOADED'
            WHERE processing_status = 'STAGED'
              AND (? IS NULL OR batch_id = ?)
            """;
        jdbcTemplate.update(sql, batchId, batchId);
    }
}
