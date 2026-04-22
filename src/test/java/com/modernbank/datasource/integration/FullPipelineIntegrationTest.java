package com.modernbank.datasource.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.modernbank.datasource.model.ods.OdsCollateralRecord;
import com.modernbank.datasource.model.ods.OdsCustomerRecord;
import com.modernbank.datasource.model.ods.OdsLoanRecord;
import com.modernbank.datasource.repository.ods.OdsCollateralRepository;
import com.modernbank.datasource.repository.ods.OdsCustomerRepository;
import com.modernbank.datasource.repository.ods.OdsLoanRepository;
import com.modernbank.datasource.service.StagingToMvService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test that exercises the full pipeline:
 * ODS -> Staging (via Spring Batch) -> MVs (via StagingToMvService)
 */
@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class FullPipelineIntegrationTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job odsStagingJob;

    @Autowired
    private OdsLoanRepository odsLoanRepository;

    @Autowired
    private OdsCustomerRepository odsCustomerRepository;

    @Autowired
    private OdsCollateralRepository odsCollateralRepository;

    @Autowired
    private StagingToMvService stagingToMvService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(odsStagingJob);

        jdbcTemplate.update("DELETE FROM mv_collateral.collateral_registry");
        jdbcTemplate.update("DELETE FROM mv_customer.customer_profile");
        jdbcTemplate.update("DELETE FROM mv_loan.loan_performance");
        jdbcTemplate.update("DELETE FROM mv_loan.loan_summary");
        jdbcTemplate.update("DELETE FROM staging.stg_collateral");
        jdbcTemplate.update("DELETE FROM staging.stg_customer");
        jdbcTemplate.update("DELETE FROM staging.stg_loan");
        odsCollateralRepository.deleteAll();
        odsCustomerRepository.deleteAll();
        odsLoanRepository.deleteAll();
    }

    @Test
    void fullPipeline_odsToStagingToMv_worksEndToEnd() throws Exception {
        // Seed ODS with loan + JSON blob
        OdsLoanRecord loan = new OdsLoanRecord();
        loan.setLoanNumber("E2E-LN-001");
        loan.setLoanStatus("ACTIVE");
        loan.setOriginationDate(LocalDate.of(2024, 1, 1));
        loan.setMaturityDate(LocalDate.of(2054, 1, 1));
        loan.setOutstandingBalance(new BigDecimal("280000.00"));
        loan.setLoanDetailsJson("""
            {
                "loan_type": "MORTGAGE",
                "loan_amount": "300000.00",
                "interest_rate": "3.75",
                "term_months": "360",
                "payment_frequency": "MONTHLY",
                "monthly_payment": "1389.35",
                "customer_id": "E2E-CUST-001",
                "risk_rating": "A",
                "branch_code": "BR-001",
                "officer_id": "OFF-001",
                "delinquency_days": "0"
            }
            """);
        loan.setProcessed(false);
        odsLoanRepository.save(loan);

        // Seed ODS with customer + JSON blob
        OdsCustomerRecord customer = new OdsCustomerRecord();
        customer.setCustomerId("E2E-CUST-001");
        customer.setCustomerName("Integration Test User");
        customer.setCreditScore(780);
        customer.setAnnualIncome(new BigDecimal("120000.00"));
        customer.setCustomerDetailsJson("""
            {
                "date_of_birth": "1980-05-20",
                "email": "e2e@test.com",
                "phone": "555-1234",
                "address_line": "100 Integration Blvd",
                "city": "TestCity",
                "state": "TC",
                "zip_code": "99999",
                "employer": "Test Corp"
            }
            """);
        customer.setProcessed(false);
        odsCustomerRepository.save(customer);

        // Seed ODS with collateral + JSON blob
        OdsCollateralRecord collateral = new OdsCollateralRecord();
        collateral.setCollateralId("E2E-COL-001");
        collateral.setLoanNumber("E2E-LN-001");
        collateral.setCollateralType("REAL_ESTATE");
        collateral.setAppraisedValue(new BigDecimal("400000.00"));
        collateral.setAppraisalDate(LocalDate.of(2023, 12, 1));
        collateral.setCollateralDetailsJson("""
            {
                "description": "4BR/3BA Colonial, 2500 sqft",
                "address": "100 Integration Blvd, TestCity, TC 99999"
            }
            """);
        collateral.setProcessed(false);
        odsCollateralRepository.save(collateral);

        // Step 1: Run batch job (ODS -> Staging)
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = jobLauncherTestUtils.launchJob(params);
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        // Verify staging tables populated
        Integer stgLoanCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging.stg_loan WHERE loan_number = 'E2E-LN-001'",
                Integer.class);
        assertEquals(1, stgLoanCount);

        Integer stgCustCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging.stg_customer WHERE customer_id = 'E2E-CUST-001'",
                Integer.class);
        assertEquals(1, stgCustCount);

        Integer stgColCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging.stg_collateral WHERE collateral_id = 'E2E-COL-001'",
                Integer.class);
        assertEquals(1, stgColCount);

        // Verify JSON data was extracted correctly into staging
        String loanType = jdbcTemplate.queryForObject(
                "SELECT loan_type FROM staging.stg_loan WHERE loan_number = 'E2E-LN-001'",
                String.class);
        assertEquals("MORTGAGE", loanType);

        String custEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM staging.stg_customer WHERE customer_id = 'E2E-CUST-001'",
                String.class);
        assertEquals("e2e@test.com", custEmail);

        // Step 2: Load MVs from staging
        stagingToMvService.loadAllMaterializedViews(null);

        // Verify MV tables populated
        Integer mvLoanCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_loan.loan_summary WHERE loan_number = 'E2E-LN-001'",
                Integer.class);
        assertEquals(1, mvLoanCount);

        Integer mvPerfCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_loan.loan_performance WHERE loan_number = 'E2E-LN-001'",
                Integer.class);
        assertEquals(1, mvPerfCount);

        Integer mvCustCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_customer.customer_profile "
                + "WHERE customer_id = 'E2E-CUST-001'",
                Integer.class);
        assertEquals(1, mvCustCount);

        Integer mvColCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_collateral.collateral_registry "
                + "WHERE collateral_id = 'E2E-COL-001'",
                Integer.class);
        assertEquals(1, mvColCount);

        // Verify data integrity in MVs
        String mvLoanType = jdbcTemplate.queryForObject(
                "SELECT loan_type FROM mv_loan.loan_summary WHERE loan_number = 'E2E-LN-001'",
                String.class);
        assertEquals("MORTGAGE", mvLoanType);

        String mvCustName = jdbcTemplate.queryForObject(
                "SELECT customer_name FROM mv_customer.customer_profile "
                + "WHERE customer_id = 'E2E-CUST-001'",
                String.class);
        assertEquals("Integration Test User", mvCustName);

        // Verify staging status updated
        String loanStatus = jdbcTemplate.queryForObject(
                "SELECT processing_status FROM staging.stg_loan WHERE loan_number = 'E2E-LN-001'",
                String.class);
        assertEquals("MV_LOADED", loanStatus);
    }

    @Test
    void fullPipeline_withEmptyOds_completesWithoutErrors() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = jobLauncherTestUtils.launchJob(params);
        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        stagingToMvService.loadAllMaterializedViews(null);

        Integer mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_loan.loan_summary", Integer.class);
        assertEquals(0, mvCount);
    }
}
