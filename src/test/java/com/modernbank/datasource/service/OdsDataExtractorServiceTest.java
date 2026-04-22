package com.modernbank.datasource.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernbank.datasource.model.ods.OdsCollateralRecord;
import com.modernbank.datasource.model.ods.OdsCustomerRecord;
import com.modernbank.datasource.model.ods.OdsLoanRecord;
import com.modernbank.datasource.model.staging.StagingCollateral;
import com.modernbank.datasource.model.staging.StagingCustomer;
import com.modernbank.datasource.model.staging.StagingLoan;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OdsDataExtractorServiceTest {

    private OdsDataExtractorService service;

    @BeforeEach
    void setUp() {
        service = new OdsDataExtractorService(new ObjectMapper());
    }

    @Test
    void transformLoan_withJsonBlob_extractsAllFields() {
        OdsLoanRecord ods = new OdsLoanRecord();
        ods.setLoanNumber("LN-001");
        ods.setLoanStatus("ACTIVE");
        ods.setOriginationDate(LocalDate.of(2023, 1, 15));
        ods.setMaturityDate(LocalDate.of(2053, 1, 15));
        ods.setOutstandingBalance(new BigDecimal("245000.00"));
        ods.setLoanDetailsJson("""
            {
                "loan_type": "MORTGAGE",
                "loan_amount": "300000.00",
                "interest_rate": "4.5000",
                "term_months": "360",
                "payment_frequency": "MONTHLY",
                "monthly_payment": "1520.06",
                "customer_id": "CUST-001",
                "risk_rating": "A",
                "branch_code": "BR-100",
                "officer_id": "OFF-050",
                "delinquency_days": "0"
            }
            """);

        StagingLoan result = service.transformLoan(ods, 1L);

        assertNotNull(result);
        assertEquals("LN-001", result.getLoanNumber());
        assertEquals("ACTIVE", result.getLoanStatus());
        assertEquals("MORTGAGE", result.getLoanType());
        assertEquals(new BigDecimal("300000.00"), result.getLoanAmount());
        assertEquals(new BigDecimal("4.5000"), result.getInterestRate());
        assertEquals(360, result.getTermMonths());
        assertEquals("MONTHLY", result.getPaymentFrequency());
        assertEquals(new BigDecimal("1520.06"), result.getMonthlyPayment());
        assertEquals("CUST-001", result.getCustomerId());
        assertEquals("A", result.getRiskRating());
        assertEquals("BR-100", result.getBranchCode());
        assertEquals("OFF-050", result.getOfficerId());
        assertEquals(0, result.getDelinquencyDays());
        assertEquals(LocalDate.of(2023, 1, 15), result.getOriginationDate());
        assertEquals(LocalDate.of(2053, 1, 15), result.getMaturityDate());
        assertEquals(new BigDecimal("245000.00"), result.getOutstandingBalance());
        assertEquals(1L, result.getBatchId());
        assertEquals("STAGED", result.getProcessingStatus());
    }

    @Test
    void transformLoan_withNullJson_usesNormalColumnsOnly() {
        OdsLoanRecord ods = new OdsLoanRecord();
        ods.setLoanNumber("LN-002");
        ods.setLoanStatus("CLOSED");
        ods.setOutstandingBalance(BigDecimal.ZERO);
        ods.setLoanDetailsJson(null);

        StagingLoan result = service.transformLoan(ods, 2L);

        assertNotNull(result);
        assertEquals("LN-002", result.getLoanNumber());
        assertEquals("CLOSED", result.getLoanStatus());
        assertEquals(BigDecimal.ZERO, result.getOutstandingBalance());
        assertNull(result.getLoanType());
        assertNull(result.getCustomerId());
        assertEquals(2L, result.getBatchId());
    }

    @Test
    void transformLoan_withInvalidJson_handlesGracefully() {
        OdsLoanRecord ods = new OdsLoanRecord();
        ods.setLoanNumber("LN-003");
        ods.setLoanStatus("ACTIVE");
        ods.setLoanDetailsJson("not valid json {{{");

        StagingLoan result = service.transformLoan(ods, 3L);

        assertNotNull(result);
        assertEquals("LN-003", result.getLoanNumber());
        assertEquals("ACTIVE", result.getLoanStatus());
        assertNull(result.getLoanType());
    }

    @Test
    void transformCustomer_withJsonBlob_extractsAllFields() {
        OdsCustomerRecord ods = new OdsCustomerRecord();
        ods.setCustomerId("CUST-001");
        ods.setCustomerName("John Smith");
        ods.setCreditScore(750);
        ods.setAnnualIncome(new BigDecimal("85000.00"));
        ods.setCustomerDetailsJson("""
            {
                "date_of_birth": "1985-06-15",
                "email": "john.smith@example.com",
                "phone": "555-0101",
                "address_line": "123 Main St",
                "city": "Springfield",
                "state": "IL",
                "zip_code": "62701",
                "employer": "Acme Corp"
            }
            """);

        StagingCustomer result = service.transformCustomer(ods, 1L);

        assertNotNull(result);
        assertEquals("CUST-001", result.getCustomerId());
        assertEquals("John Smith", result.getCustomerName());
        assertEquals(750, result.getCreditScore());
        assertEquals(new BigDecimal("85000.00"), result.getAnnualIncome());
        assertEquals(LocalDate.of(1985, 6, 15), result.getDateOfBirth());
        assertEquals("john.smith@example.com", result.getEmail());
        assertEquals("555-0101", result.getPhone());
        assertEquals("123 Main St", result.getAddressLine());
        assertEquals("Springfield", result.getCity());
        assertEquals("IL", result.getState());
        assertEquals("62701", result.getZipCode());
        assertEquals("Acme Corp", result.getEmployer());
        assertEquals("STAGED", result.getProcessingStatus());
    }

    @Test
    void transformCustomer_withEmptyJson_usesNormalColumnsOnly() {
        OdsCustomerRecord ods = new OdsCustomerRecord();
        ods.setCustomerId("CUST-002");
        ods.setCustomerName("Jane Doe");
        ods.setCreditScore(680);
        ods.setCustomerDetailsJson("");

        StagingCustomer result = service.transformCustomer(ods, 2L);

        assertNotNull(result);
        assertEquals("CUST-002", result.getCustomerId());
        assertEquals("Jane Doe", result.getCustomerName());
        assertEquals(680, result.getCreditScore());
        assertNull(result.getEmail());
        assertNull(result.getCity());
    }

    @Test
    void transformCollateral_withJsonBlob_extractsAllFields() {
        OdsCollateralRecord ods = new OdsCollateralRecord();
        ods.setCollateralId("COL-001");
        ods.setLoanNumber("LN-001");
        ods.setCollateralType("REAL_ESTATE");
        ods.setAppraisedValue(new BigDecimal("350000.00"));
        ods.setAppraisalDate(LocalDate.of(2022, 11, 20));
        ods.setCollateralDetailsJson("""
            {
                "description": "Single family home, 3BR/2BA",
                "address": "456 Oak Ave, Springfield, IL 62701"
            }
            """);

        StagingCollateral result = service.transformCollateral(ods, 1L);

        assertNotNull(result);
        assertEquals("COL-001", result.getCollateralId());
        assertEquals("LN-001", result.getLoanNumber());
        assertEquals("REAL_ESTATE", result.getCollateralType());
        assertEquals(new BigDecimal("350000.00"), result.getAppraisedValue());
        assertEquals(LocalDate.of(2022, 11, 20), result.getAppraisalDate());
        assertEquals("Single family home, 3BR/2BA", result.getDescription());
        assertEquals("456 Oak Ave, Springfield, IL 62701", result.getAddress());
        assertEquals("STAGED", result.getProcessingStatus());
    }

    @Test
    void transformCollateral_withNullJson_usesNormalColumnsOnly() {
        OdsCollateralRecord ods = new OdsCollateralRecord();
        ods.setCollateralId("COL-002");
        ods.setLoanNumber("LN-002");
        ods.setCollateralType("VEHICLE");
        ods.setAppraisedValue(new BigDecimal("25000.00"));
        ods.setCollateralDetailsJson(null);

        StagingCollateral result = service.transformCollateral(ods, 2L);

        assertNotNull(result);
        assertEquals("COL-002", result.getCollateralId());
        assertEquals("VEHICLE", result.getCollateralType());
        assertNull(result.getDescription());
        assertNull(result.getAddress());
    }

    @Test
    void transformLoan_withPartialJson_extractsAvailableFields() {
        OdsLoanRecord ods = new OdsLoanRecord();
        ods.setLoanNumber("LN-004");
        ods.setLoanStatus("ACTIVE");
        ods.setLoanDetailsJson("""
            {
                "loan_type": "AUTO",
                "customer_id": "CUST-005"
            }
            """);

        StagingLoan result = service.transformLoan(ods, 4L);

        assertNotNull(result);
        assertEquals("AUTO", result.getLoanType());
        assertEquals("CUST-005", result.getCustomerId());
        assertNull(result.getLoanAmount());
        assertNull(result.getInterestRate());
        assertNull(result.getTermMonths());
    }

    @Test
    void transformCustomer_withInvalidDateInJson_handlesGracefully() {
        OdsCustomerRecord ods = new OdsCustomerRecord();
        ods.setCustomerId("CUST-003");
        ods.setCustomerName("Bad Date");
        ods.setCustomerDetailsJson("""
            {
                "date_of_birth": "not-a-date",
                "email": "valid@email.com"
            }
            """);

        StagingCustomer result = service.transformCustomer(ods, 3L);

        assertNotNull(result);
        assertEquals("CUST-003", result.getCustomerId());
        assertNull(result.getDateOfBirth());
        assertEquals("valid@email.com", result.getEmail());
    }
}
