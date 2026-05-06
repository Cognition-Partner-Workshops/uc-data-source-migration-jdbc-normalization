package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that verify the service layer handles the actual
 * legacy seed data — including its known anomalies — without crashing.
 */
@SpringBootTest
class LoanServiceIntegrationTest {

    @Autowired
    private LoanService loanService;

    @Test
    @DisplayName("getAllLoans returns all 5 loans without throwing")
    void getAllLoans() {
        List<LoanSummaryDto> loans = loanService.getAllLoans();
        assertEquals(5, loans.size());
        loans.forEach(loan -> {
            assertNotNull(loan.getLoanAccountNumber());
            assertNotNull(loan.getBorrowerName());
            assertFalse(loan.getBorrowerName().contains("null"), "Name should not contain literal 'null'");
            assertNotNull(loan.getOriginalAmount());
            assertTrue(loan.getOriginalAmount().compareTo(BigDecimal.ZERO) > 0);
        });
    }

    @Test
    @DisplayName("getAllBorrowers returns all 5 borrowers without throwing")
    void getAllBorrowers() {
        List<BorrowerDto> borrowers = loanService.getAllBorrowers();
        assertEquals(5, borrowers.size());
        borrowers.forEach(b -> {
            assertNotNull(b.getId());
            assertNotNull(b.getFullName());
            assertFalse(b.getFullName().contains("null"), "Name should not contain literal 'null'");
        });
    }

    @Test
    @DisplayName("getBorrowerById returns correct borrower with loans attached")
    void getBorrowerById() {
        BorrowerDto dto = loanService.getBorrowerById("B-10001");
        assertEquals("B-10001", dto.getId());
        assertTrue(dto.getFullName().contains("James"));
        assertTrue(dto.getFullName().contains("Mitchell"));
        assertNotNull(dto.getLoans());
        assertFalse(dto.getLoans().isEmpty());
    }

    @Test
    @DisplayName("getLoanById returns correct loan with parsed amounts")
    void getLoanById() {
        LoanSummaryDto dto = loanService.getLoanById("LN-2019-00142");
        assertEquals("LN-2019-00142", dto.getLoanAccountNumber());
        assertEquals(new BigDecimal("285000"), dto.getOriginalAmount());
        assertTrue(dto.getInterestRate().compareTo(BigDecimal.ZERO) > 0);
        assertNotNull(dto.getStatus());
        assertNotNull(dto.getOriginationDate());
    }

    @Test
    @DisplayName("dates are returned in ISO format instead of MM/DD/YYYY")
    void datesConvertedToIso() {
        LoanSummaryDto loan = loanService.getLoanById("LN-2019-00142");
        assertTrue(loan.getOriginationDate().matches("\\d{4}-\\d{2}-\\d{2}"),
                "Origination date should be ISO format: " + loan.getOriginationDate());

        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2019-00142");
        assertFalse(payments.isEmpty());
        payments.forEach(p -> assertTrue(p.getPaymentDate().matches("\\d{4}-\\d{2}-\\d{2}"),
                "Payment date should be ISO format: " + p.getPaymentDate()));
    }

    @Test
    @DisplayName("payments are sorted in reverse chronological order")
    void paymentsSortedDescending() {
        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2019-00142");
        assertEquals(2, payments.size());
        assertTrue(payments.get(0).getPaymentDate().compareTo(payments.get(1).getPaymentDate()) >= 0,
                "First payment should be more recent");
    }

    @Test
    @DisplayName("payment component mismatch in LN-2019-00142 does not crash the service")
    void paymentMismatchDoesNotCrash() {
        List<PaymentDto> payments = loanService.getPaymentsByLoan("LN-2019-00142");
        assertFalse(payments.isEmpty());

        PaymentDto pmt = payments.get(0);
        BigDecimal componentSum = pmt.getPrincipalAmount()
                .add(pmt.getInterestAmount())
                .add(pmt.getEscrowAmount());
        BigDecimal delta = componentSum.subtract(pmt.getTotalAmount()).abs();

        assertTrue(delta.compareTo(new BigDecimal("0.02")) > 0,
                "Known anomaly: payment components should NOT sum to total for this loan");
    }

    @Test
    @DisplayName("service handles non-existent loan ID with clear exception")
    void nonExistentLoanThrows() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loanService.getLoanById("LN-NONEXISTENT"));
        assertTrue(ex.getMessage().contains("Loan not found"));
    }

    @Test
    @DisplayName("service handles non-existent borrower ID with clear exception")
    void nonExistentBorrowerThrows() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> loanService.getBorrowerById("B-99999"));
        assertTrue(ex.getMessage().contains("Borrower not found"));
    }
}
