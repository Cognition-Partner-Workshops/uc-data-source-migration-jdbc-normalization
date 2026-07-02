package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that verify proper date and numeric ordering for VARCHAR columns
 * in the legacy CDW_PMT_HIST table. Without PARSEDATETIME/CAST, string
 * comparison of MM/DD/YYYY dates and numeric amounts gives wrong results.
 */
@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LegacyPaymentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LegacyPaymentRepository repository;

    @BeforeEach
    void setUp() {
        entityManager.getEntityManager()
                .createNativeQuery("DELETE FROM CDW_PMT_HIST")
                .executeUpdate();

        // Dates chosen so string sort differs from chronological sort:
        //   String DESC: "12/01/2024", "11/15/2025", "02/28/2025", "01/05/2026"
        //   Date   DESC: 01/05/2026, 11/15/2025, 02/28/2025, 12/01/2024
        insertPayment("PMT-001", "LN-TEST", "12/01/2024", "1,500.00");
        insertPayment("PMT-002", "LN-TEST", "02/28/2025", "99.50");
        insertPayment("PMT-003", "LN-TEST", "11/15/2025", "1,000.00");
        insertPayment("PMT-004", "LN-TEST", "01/05/2026", "250.75");
    }

    @Test
    void orderByPaymentDateDesc_usesChronologicalSort() {
        List<LegacyPayment> results =
                repository.findByLoanAccountNumberOrderByPaymentDateDesc("LN-TEST");

        assertEquals(4, results.size());
        assertEquals("PMT-004", results.get(0).getPaymentSequenceNumber()); // 01/05/2026
        assertEquals("PMT-003", results.get(1).getPaymentSequenceNumber()); // 11/15/2025
        assertEquals("PMT-002", results.get(2).getPaymentSequenceNumber()); // 02/28/2025
        assertEquals("PMT-001", results.get(3).getPaymentSequenceNumber()); // 12/01/2024
    }

    @Test
    void orderByTotalAmountDesc_usesNumericSort() {
        List<LegacyPayment> results =
                repository.findByLoanAccountNumberOrderByTotalAmountDesc("LN-TEST");

        assertEquals(4, results.size());
        assertEquals("PMT-001", results.get(0).getPaymentSequenceNumber()); // 1,500.00
        assertEquals("PMT-003", results.get(1).getPaymentSequenceNumber()); // 1,000.00
        assertEquals("PMT-004", results.get(2).getPaymentSequenceNumber()); // 250.75
        assertEquals("PMT-002", results.get(3).getPaymentSequenceNumber()); // 99.50
    }

    @Test
    void dateSort_producesCorrectChronologicalOrder() {
        List<LegacyPayment> results =
                repository.findByLoanAccountNumberOrderByPaymentDateDesc("LN-TEST");

        String prevDate = "99/99/9999";
        for (LegacyPayment pmt : results) {
            String[] parts = pmt.getPaymentDate().split("/");
            String isoDate = parts[2] + "-" + parts[0] + "-" + parts[1];
            assertTrue(isoDate.compareTo(prevDate) <= 0,
                    "Dates should be in descending chronological order");
            prevDate = isoDate;
        }
    }

    @Test
    void amountSort_producesCorrectNumericOrder() {
        List<LegacyPayment> results =
                repository.findByLoanAccountNumberOrderByTotalAmountDesc("LN-TEST");

        BigDecimal prev = new BigDecimal("999999999");
        for (LegacyPayment pmt : results) {
            BigDecimal amount = new BigDecimal(pmt.getTotalAmount().replace(",", ""));
            assertTrue(amount.compareTo(prev) <= 0,
                    "Amount " + amount + " should be <= " + prev);
            prev = amount;
        }
    }

    private void insertPayment(String seqNbr, String loanAcct, String pmtDate, String amount) {
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO CDW_PMT_HIST " +
                        "(PMT_SEQ_NBR, LN_ACCT_NBR, PMT_DT, PMT_AMT, PMT_PRIN_AMT, " +
                        "PMT_INT_AMT, PMT_ESCROW_AMT, PMT_LATE_FEE, PMT_TYP_CD, " +
                        "PMT_STAT_CD, PMT_RECV_DT, PMT_PROC_DT, PMT_CRET_DT, PMT_UPDT_DT) " +
                        "VALUES (?1, ?2, ?3, ?4, '0.00', '0.00', '0.00', '0.00', 'REG', " +
                        "'PST', ?3, ?3, ?3, ?3)")
                .setParameter(1, seqNbr)
                .setParameter(2, loanAcct)
                .setParameter(3, pmtDate)
                .setParameter(4, amount)
                .executeUpdate();
    }
}
