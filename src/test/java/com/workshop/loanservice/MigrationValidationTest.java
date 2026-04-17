package com.workshop.loanservice;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.LoanProduct;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import com.workshop.loanservice.service.DataMigrationService;
import com.workshop.loanservice.service.LoanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation tests that verify data integrity after the legacy-to-modern migration.
 *
 * These tests confirm:
 * 1. Row counts match expected numbers (5 borrowers, 5 products, 5 accounts, 10 payments)
 * 2. API endpoints return correct data from the modern schema
 * 3. Data values match the legacy seed data after type conversion
 * 4. Foreign key relationships are correctly resolved
 * 5. API response format is preserved (same field names and shapes)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class MigrationValidationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BorrowerRepository borrowerRepository;

    @Autowired
    private LoanProductRepository loanProductRepository;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private LoanService loanService;

    // =========================================================================
    // Row count validation
    // =========================================================================

    @Test
    void migrationShouldProduceCorrectBorrowerCount() {
        List<Borrower> borrowers = borrowerRepository.findAll();
        assertEquals(5, borrowers.size(), "Expected 5 borrowers migrated from legacy");
    }

    @Test
    void migrationShouldProduceCorrectProductCount() {
        List<LoanProduct> products = loanProductRepository.findAll();
        assertEquals(5, products.size(), "Expected 5 loan products migrated from legacy");
    }

    @Test
    void migrationShouldProduceCorrectAccountCount() {
        List<LoanAccount> accounts = loanAccountRepository.findAll();
        assertEquals(5, accounts.size(), "Expected 5 loan accounts migrated from legacy");
    }

    @Test
    void migrationShouldProduceCorrectPaymentCount() {
        List<Payment> payments = paymentRepository.findAll();
        assertEquals(10, payments.size(), "Expected 10 payments migrated from legacy");
    }

    // =========================================================================
    // Borrower data integrity
    // =========================================================================

    @Test
    void borrowerDataShouldMatchLegacyAfterMigration() {
        Borrower borrower = borrowerRepository.findByExternalId("B-10001").orElseThrow();

        assertEquals("James", borrower.getFirstName());
        assertEquals("Mitchell", borrower.getLastName());
        assertEquals("R", borrower.getMiddleInitial());
        assertEquals("j.mitchell@email.com", borrower.getEmail());
        assertEquals(745, borrower.getCreditScore());
        assertEquals("EMPLOYED", borrower.getEmploymentStatus());
        assertEquals(0, new BigDecimal("92500").compareTo(borrower.getAnnualIncome()));
        assertEquals("ACTIVE", borrower.getStatus());
        assertEquals("Springfield", borrower.getCity());
        assertEquals("IL", borrower.getState());
        assertEquals("62701", borrower.getZipCode());
        assertEquals("217-555-0142", borrower.getPhone());
        assertNotNull(borrower.getDateOfBirth());
        assertEquals(1978, borrower.getDateOfBirth().getYear());
        assertEquals(3, borrower.getDateOfBirth().getMonthValue());
        assertEquals(15, borrower.getDateOfBirth().getDayOfMonth());
    }

    @Test
    void borrowerWithNullMiddleInitialShouldMigrateCorrectly() {
        Borrower borrower = borrowerRepository.findByExternalId("B-10005").orElseThrow();

        assertEquals("Robert", borrower.getFirstName());
        assertEquals("Williams", borrower.getLastName());
        assertNull(borrower.getMiddleInitial());
        assertEquals("RETIRED", borrower.getEmploymentStatus());
        assertEquals(658, borrower.getCreditScore());
    }

    // =========================================================================
    // Loan product data integrity
    // =========================================================================

    @Test
    void loanProductDataShouldMatchLegacy() {
        LoanProduct product = loanProductRepository.findByCode("FXD30").orElseThrow();

        assertEquals("30-Year Fixed Rate Mortgage", product.getName());
        assertEquals("FXD", product.getType());
        assertEquals(360, product.getTermMonths());
        assertEquals("FIXED", product.getRateType());
        assertEquals(0, new BigDecimal("50000").compareTo(product.getMinAmount()));
        assertEquals(0, new BigDecimal("1500000").compareTo(product.getMaxAmount()));
        assertTrue(product.getIsActive());
    }

    @Test
    void allProductsShouldHaveProperTypes() {
        List<LoanProduct> products = loanProductRepository.findAll();
        for (LoanProduct product : products) {
            assertNotNull(product.getCode(), "Product code should not be null");
            assertNotNull(product.getName(), "Product name should not be null");
            assertNotNull(product.getType(), "Product type should not be null");
            assertNotNull(product.getTermMonths(), "Term months should not be null");
            assertNotNull(product.getRateType(), "Rate type should not be null");
            assertNotNull(product.getIsActive(), "isActive should not be null");
        }
    }

    // =========================================================================
    // Loan account data integrity and FK validation
    // =========================================================================

    @Test
    void loanAccountShouldHaveCorrectForeignKeys() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();

        assertNotNull(account.getBorrower(), "Borrower FK should be resolved");
        assertEquals("B-10001", account.getBorrower().getExternalId());

        assertNotNull(account.getProduct(), "Product FK should be resolved");
        assertEquals("FXD30", account.getProduct().getCode());
    }

    @Test
    void loanAccountAmountsShouldMatchLegacy() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();

        assertEquals(0, new BigDecimal("285000").compareTo(account.getOriginalAmount()));
        assertEquals(0, new BigDecimal("271432.56").compareTo(account.getCurrentBalance()));
        assertEquals(0, new BigDecimal("4.750").compareTo(account.getInterestRate()));
        assertEquals(360, account.getTermMonths());
        assertEquals(new BigDecimal("1487.02"), account.getMonthlyPayment());
        assertEquals("ACTIVE", account.getStatus());
        assertEquals(0, account.getDelinquencyDays());
    }

    @Test
    void loanAccountPropertyDataShouldMatchLegacy() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();

        assertEquals("742 Elm Street", account.getPropertyAddress());
        assertEquals("Springfield", account.getPropertyCity());
        assertEquals("IL", account.getPropertyState());
        assertEquals("62701", account.getPropertyZip());
        assertEquals("Single Family", account.getPropertyType());
        assertEquals(0, new BigDecimal("345000").compareTo(account.getAppraisedValue()));
    }

    @Test
    void loanAccountDatesShouldBeParsedCorrectly() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2019-00142").orElseThrow();

        assertNotNull(account.getOriginationDate());
        assertEquals(2019, account.getOriginationDate().getYear());
        assertEquals(2, account.getOriginationDate().getMonthValue());
        assertEquals(15, account.getOriginationDate().getDayOfMonth());

        assertNotNull(account.getMaturityDate());
        assertEquals(2049, account.getMaturityDate().getYear());
    }

    // =========================================================================
    // Payment data integrity
    // =========================================================================

    @Test
    void paymentsShouldHaveCorrectLoanAccountFK() {
        List<Payment> payments = paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN-2019-00142");

        assertEquals(2, payments.size(), "Expected 2 payments for loan LN-2019-00142");
        for (Payment payment : payments) {
            assertEquals("LN-2019-00142", payment.getLoanAccount().getAccountNumber());
        }
    }

    @Test
    void paymentAmountsShouldMatchLegacy() {
        List<Payment> payments = paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN-2019-00142");

        Payment decPayment = payments.stream()
                .filter(p -> p.getPaymentDate().getMonthValue() == 12)
                .findFirst().orElseThrow();

        assertEquals(new BigDecimal("1487.02"), decPayment.getTotalAmount());
        assertEquals(new BigDecimal("456.78"), decPayment.getPrincipalAmount());
        assertEquals(new BigDecimal("1074.69"), decPayment.getInterestAmount());
        assertEquals(new BigDecimal("355.55"), decPayment.getEscrowAmount());
        assertEquals(new BigDecimal("0.00"), decPayment.getLateFee());
        assertEquals("REGULAR", decPayment.getType());
        assertEquals("POSTED", decPayment.getStatus());
    }

    @Test
    void paymentWithLateFeesShouldMigrateCorrectly() {
        List<Payment> payments = paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDesc("LN-2018-00089");

        Payment novPayment = payments.stream()
                .filter(p -> p.getPaymentDate().getMonthValue() == 11)
                .findFirst().orElseThrow();

        assertEquals(new BigDecimal("47.50"), novPayment.getLateFee());
    }

    // =========================================================================
    // API endpoint validation (golden file comparison)
    // Verifies that the REST API returns the same response shape and values
    // as the legacy system after migration.
    // =========================================================================

    @Test
    void getAllLoansEndpointShouldReturnFiveLoans() {
        ResponseEntity<List<LoanSummaryDto>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/loans",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().size());
    }

    @Test
    void getLoanByIdEndpointShouldReturnCorrectData() {
        ResponseEntity<LoanSummaryDto> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/loans/LN-2019-00142",
                LoanSummaryDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoanSummaryDto loan = response.getBody();
        assertNotNull(loan);

        assertEquals("LN-2019-00142", loan.getLoanAccountNumber());
        assertEquals("James Mitchell", loan.getBorrowerName());
        assertEquals("30-Year Fixed Rate Mortgage", loan.getProductDescription());
        assertEquals(0, new BigDecimal("285000").compareTo(loan.getOriginalAmount()));
        assertEquals(0, new BigDecimal("271432.56").compareTo(loan.getCurrentBalance()));
        assertEquals(0, new BigDecimal("4.750").compareTo(loan.getInterestRate()));
        assertEquals(0, new BigDecimal("1487.02").compareTo(loan.getMonthlyPayment()));
        assertEquals("Active", loan.getStatus());
        assertEquals("02/15/2019", loan.getOriginationDate());
        assertEquals("Single Family Residence", loan.getPropertyType());
        assertTrue(loan.getPropertyAddress().contains("742 Elm Street"));
        assertTrue(loan.getPropertyAddress().contains("Springfield"));
    }

    @Test
    void getAllBorrowersEndpointShouldReturnFiveBorrowers() {
        ResponseEntity<List<BorrowerDto>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/borrowers",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().size());
    }

    @Test
    void getBorrowerByIdEndpointShouldReturnCorrectData() {
        ResponseEntity<BorrowerDto> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/borrowers/B-10001",
                BorrowerDto.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BorrowerDto borrower = response.getBody();
        assertNotNull(borrower);

        assertEquals("B-10001", borrower.getId());
        assertEquals("James R. Mitchell", borrower.getFullName());
        assertEquals("j.mitchell@email.com", borrower.getEmail());
        assertEquals("217-555-0142", borrower.getPhone());
        assertEquals("Springfield", borrower.getCity());
        assertEquals("IL", borrower.getState());
        assertEquals(745, borrower.getCreditScore());
        assertEquals("EMPLOYED", borrower.getEmploymentStatus());
    }

    @Test
    void getBorrowerByIdEndpointShouldIncludeLoans() {
        ResponseEntity<BorrowerDto> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/borrowers/B-10001",
                BorrowerDto.class);

        BorrowerDto borrower = response.getBody();
        assertNotNull(borrower);
        assertNotNull(borrower.getLoans());
        assertEquals(1, borrower.getLoans().size());
        assertEquals("LN-2019-00142", borrower.getLoans().get(0).getLoanAccountNumber());
    }

    @Test
    void getPaymentsEndpointShouldReturnCorrectData() {
        ResponseEntity<List<PaymentDto>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/loans/LN-2019-00142/payments",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());

        PaymentDto firstPayment = response.getBody().get(0);
        assertNotNull(firstPayment.getPaymentId());
        assertEquals("LN-2019-00142", firstPayment.getLoanAccountNumber());
        assertEquals("Regular", firstPayment.getType());
        assertEquals("Posted", firstPayment.getStatus());
    }

    @Test
    void borrowerWithNullMiddleInitialShouldFormatNameCorrectly() {
        ResponseEntity<BorrowerDto> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/borrowers/B-10005",
                BorrowerDto.class);

        BorrowerDto borrower = response.getBody();
        assertNotNull(borrower);
        assertEquals("Robert Williams", borrower.getFullName());
    }

    // =========================================================================
    // Cross-entity consistency checks
    // =========================================================================

    @Test
    void allLoanAccountsShouldHaveValidBorrowerReferences() {
        List<LoanAccount> accounts = loanAccountRepository.findAll();
        for (LoanAccount account : accounts) {
            assertNotNull(account.getBorrower(),
                    "Loan " + account.getAccountNumber() + " should have a borrower");
            assertNotNull(account.getBorrower().getExternalId(),
                    "Borrower for loan " + account.getAccountNumber() + " should have an external ID");
        }
    }

    @Test
    void allLoanAccountsShouldHaveValidProductReferences() {
        List<LoanAccount> accounts = loanAccountRepository.findAll();
        for (LoanAccount account : accounts) {
            assertNotNull(account.getProduct(),
                    "Loan " + account.getAccountNumber() + " should have a product");
            assertNotNull(account.getProduct().getCode(),
                    "Product for loan " + account.getAccountNumber() + " should have a code");
        }
    }

    @Test
    void allPaymentsShouldHaveValidLoanAccountReferences() {
        List<Payment> payments = paymentRepository.findAll();
        for (Payment payment : payments) {
            assertNotNull(payment.getLoanAccount(),
                    "Payment " + payment.getId() + " should have a loan account");
            assertNotNull(payment.getLoanAccount().getAccountNumber(),
                    "Loan account for payment " + payment.getId() + " should have an account number");
        }
    }

    @Test
    void secondLoanShouldMapToCorrectBorrowerAndProduct() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2020-00398").orElseThrow();

        assertEquals("B-10002", account.getBorrower().getExternalId());
        assertEquals("Sarah", account.getBorrower().getFirstName());
        assertEquals("FXD15", account.getProduct().getCode());
        assertEquals("15-Year Fixed Rate Mortgage", account.getProduct().getName());
        assertEquals(0, new BigDecimal("420000").compareTo(account.getOriginalAmount()));
        assertEquals("Condominium", account.getPropertyType());
    }

    @Test
    void delinquentLoanShouldPreserveDelinquencyDays() {
        LoanAccount account = loanAccountRepository.findByAccountNumber("LN-2018-00089").orElseThrow();

        assertEquals(15, account.getDelinquencyDays());
        assertEquals("ACTIVE", account.getStatus());
    }
}
