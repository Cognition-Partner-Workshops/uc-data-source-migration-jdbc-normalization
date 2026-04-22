package com.modernbank.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest
@ActiveProfiles("test")
class StagingToMvServiceTest {

    @Autowired
    private StagingToMvService stagingToMvService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Sql(statements = {
        "DELETE FROM mv_loan.loan_summary",
        "DELETE FROM mv_loan.loan_performance",
        "DELETE FROM staging.stg_loan",
        "INSERT INTO staging.stg_loan (loan_number, loan_type, loan_status, loan_amount, "
            + "interest_rate, term_months, origination_date, maturity_date, outstanding_balance, "
            + "customer_id, risk_rating, delinquency_days, branch_code, officer_id, batch_id, "
            + "processing_status) "
            + "VALUES ('LN-100', 'MORTGAGE', 'ACTIVE', 300000.00, 4.5, 360, '2023-01-15', "
            + "'2053-01-15', 245000.00, 'CUST-100', 'A', 0, 'BR-100', 'OFF-50', 100, 'STAGED')"
    })
    void loadLoanSummary_insertsIntoMvLoan() {
        stagingToMvService.loadLoanSummary(100L);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_loan.loan_summary WHERE loan_number = 'LN-100'",
                Integer.class);
        assertEquals(1, count);
    }

    @Test
    @Sql(statements = {
        "DELETE FROM mv_customer.customer_profile",
        "DELETE FROM staging.stg_customer",
        "INSERT INTO staging.stg_customer (customer_id, customer_name, email, credit_score, "
            + "annual_income, city, state, zip_code, batch_id, processing_status) "
            + "VALUES ('CUST-200', 'Alice Johnson', 'alice@example.com', 720, 90000.00, "
            + "'Chicago', 'IL', '60601', 200, 'STAGED')"
    })
    void loadCustomerProfile_insertsIntoMvCustomer() {
        stagingToMvService.loadCustomerProfile(200L);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_customer.customer_profile WHERE customer_id = 'CUST-200'",
                Integer.class);
        assertEquals(1, count);

        String status = jdbcTemplate.queryForObject(
                "SELECT processing_status FROM staging.stg_customer WHERE customer_id = 'CUST-200'",
                String.class);
        assertEquals("MV_LOADED", status);
    }

    @Test
    @Sql(statements = {
        "DELETE FROM mv_collateral.collateral_registry",
        "DELETE FROM staging.stg_collateral",
        "INSERT INTO staging.stg_collateral (collateral_id, loan_number, collateral_type, "
            + "description, appraised_value, address, batch_id, processing_status) "
            + "VALUES ('COL-300', 'LN-300', 'REAL_ESTATE', '3BR House', 350000.00, "
            + "'789 Pine St', 300, 'STAGED')"
    })
    void loadCollateralRegistry_insertsIntoMvCollateral() {
        stagingToMvService.loadCollateralRegistry(300L);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_collateral.collateral_registry "
                + "WHERE collateral_id = 'COL-300'",
                Integer.class);
        assertEquals(1, count);

        String status = jdbcTemplate.queryForObject(
                "SELECT processing_status FROM staging.stg_collateral "
                + "WHERE collateral_id = 'COL-300'",
                String.class);
        assertEquals("MV_LOADED", status);
    }

    @Test
    @Sql(statements = {
        "DELETE FROM mv_loan.loan_summary",
        "DELETE FROM mv_loan.loan_performance",
        "DELETE FROM mv_customer.customer_profile",
        "DELETE FROM mv_collateral.collateral_registry",
        "DELETE FROM staging.stg_loan",
        "DELETE FROM staging.stg_customer",
        "DELETE FROM staging.stg_collateral",
        "INSERT INTO staging.stg_loan (loan_number, loan_type, loan_status, loan_amount, "
            + "outstanding_balance, customer_id, risk_rating, delinquency_days, batch_id, "
            + "processing_status) "
            + "VALUES ('LN-400', 'AUTO', 'ACTIVE', 25000.00, 18000.00, 'CUST-400', 'B', "
            + "0, 400, 'STAGED')",
        "INSERT INTO staging.stg_customer (customer_id, customer_name, credit_score, "
            + "batch_id, processing_status) "
            + "VALUES ('CUST-400', 'Bob Williams', 680, 400, 'STAGED')",
        "INSERT INTO staging.stg_collateral (collateral_id, loan_number, collateral_type, "
            + "appraised_value, batch_id, processing_status) "
            + "VALUES ('COL-400', 'LN-400', 'VEHICLE', 22000.00, 400, 'STAGED')"
    })
    void loadAllMaterializedViews_populatesAllMvTables() {
        stagingToMvService.loadAllMaterializedViews(400L);

        Integer loanCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_loan.loan_summary WHERE loan_number = 'LN-400'",
                Integer.class);
        assertEquals(1, loanCount);

        Integer perfCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_loan.loan_performance WHERE loan_number = 'LN-400'",
                Integer.class);
        assertEquals(1, perfCount);

        Integer custCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_customer.customer_profile WHERE customer_id = 'CUST-400'",
                Integer.class);
        assertEquals(1, custCount);

        Integer collCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_collateral.collateral_registry "
                + "WHERE collateral_id = 'COL-400'",
                Integer.class);
        assertEquals(1, collCount);
    }

    @Test
    @Sql(statements = {
        "DELETE FROM staging.stg_loan",
        "DELETE FROM mv_loan.loan_summary"
    })
    void loadLoanSummary_withNoStagedData_doesNothing() {
        stagingToMvService.loadLoanSummary(999L);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_loan.loan_summary", Integer.class);
        assertEquals(0, count);
    }
}
