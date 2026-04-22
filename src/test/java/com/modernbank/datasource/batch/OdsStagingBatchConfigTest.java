package com.modernbank.datasource.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.modernbank.datasource.model.ods.OdsCollateralRecord;
import com.modernbank.datasource.model.ods.OdsCustomerRecord;
import com.modernbank.datasource.model.ods.OdsLoanRecord;
import com.modernbank.datasource.repository.ods.OdsCollateralRepository;
import com.modernbank.datasource.repository.ods.OdsCustomerRepository;
import com.modernbank.datasource.repository.ods.OdsLoanRepository;
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

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
class OdsStagingBatchConfigTest {

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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(odsStagingJob);

        jdbcTemplate.update("DELETE FROM staging.stg_collateral");
        jdbcTemplate.update("DELETE FROM staging.stg_customer");
        jdbcTemplate.update("DELETE FROM staging.stg_loan");
        odsLoanRepository.deleteAll();
        odsCustomerRepository.deleteAll();
        odsCollateralRepository.deleteAll();
    }

    @Test
    void odsStagingJob_processesLoanRecords() throws Exception {
        OdsLoanRecord loan = new OdsLoanRecord();
        loan.setLoanNumber("BATCH-LN-001");
        loan.setLoanStatus("ACTIVE");
        loan.setOutstandingBalance(new BigDecimal("150000.00"));
        loan.setOriginationDate(LocalDate.of(2023, 3, 1));
        loan.setLoanDetailsJson("""
            {
                "loan_type": "PERSONAL",
                "loan_amount": "200000.00",
                "interest_rate": "5.25",
                "term_months": "60",
                "customer_id": "BATCH-CUST-001"
            }
            """);
        loan.setProcessed(false);
        odsLoanRepository.save(loan);

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        Integer stgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging.stg_loan WHERE loan_number = 'BATCH-LN-001'",
                Integer.class);
        assertNotNull(stgCount);
        assertEquals(1, stgCount);

        String loanType = jdbcTemplate.queryForObject(
                "SELECT loan_type FROM staging.stg_loan WHERE loan_number = 'BATCH-LN-001'",
                String.class);
        assertEquals("PERSONAL", loanType);
    }

    @Test
    void odsStagingJob_processesCustomerRecords() throws Exception {
        OdsCustomerRecord customer = new OdsCustomerRecord();
        customer.setCustomerId("BATCH-CUST-001");
        customer.setCustomerName("Batch Test User");
        customer.setCreditScore(700);
        customer.setAnnualIncome(new BigDecimal("60000.00"));
        customer.setCustomerDetailsJson("""
            {
                "email": "batch@test.com",
                "phone": "555-9999",
                "city": "TestCity",
                "state": "TS"
            }
            """);
        customer.setProcessed(false);
        odsCustomerRepository.save(customer);

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        Integer stgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging.stg_customer "
                + "WHERE customer_id = 'BATCH-CUST-001'",
                Integer.class);
        assertNotNull(stgCount);
        assertEquals(1, stgCount);
    }

    @Test
    void odsStagingJob_processesCollateralRecords() throws Exception {
        OdsCollateralRecord collateral = new OdsCollateralRecord();
        collateral.setCollateralId("BATCH-COL-001");
        collateral.setLoanNumber("BATCH-LN-001");
        collateral.setCollateralType("EQUIPMENT");
        collateral.setAppraisedValue(new BigDecimal("50000.00"));
        collateral.setCollateralDetailsJson("""
            {
                "description": "Industrial equipment",
                "address": "123 Factory Rd"
            }
            """);
        collateral.setProcessed(false);
        odsCollateralRepository.save(collateral);

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        Integer stgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging.stg_collateral "
                + "WHERE collateral_id = 'BATCH-COL-001'",
                Integer.class);
        assertNotNull(stgCount);
        assertEquals(1, stgCount);
    }

    @Test
    void odsStagingJob_withNoUnprocessedRecords_completesSuccessfully() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
    }

    @Test
    void odsStagingJob_processesMultipleRecordsInBatch() throws Exception {
        for (int i = 1; i <= 5; i++) {
            OdsLoanRecord loan = new OdsLoanRecord();
            loan.setLoanNumber("MULTI-LN-" + i);
            loan.setLoanStatus("ACTIVE");
            loan.setOutstandingBalance(new BigDecimal(100000 * i));
            loan.setLoanDetailsJson(String.format("""
                {"loan_type": "TYPE_%d", "customer_id": "MULTI-CUST-%d"}
                """, i, i));
            loan.setProcessed(false);
            odsLoanRepository.save(loan);
        }

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());

        Integer stgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM staging.stg_loan WHERE loan_number LIKE 'MULTI-LN-%'",
                Integer.class);
        assertEquals(5, stgCount);
    }
}
