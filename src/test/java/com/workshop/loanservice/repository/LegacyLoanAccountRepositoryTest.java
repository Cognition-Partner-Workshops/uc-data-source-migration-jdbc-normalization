package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanAccount;
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
 * Tests that verify numeric CAST-based ordering for VARCHAR columns
 * in the legacy CDW_LN_ACCT table. Without CAST, string comparison
 * would produce wrong ordering (e.g. "99.50" > "1000.00").
 */
@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LegacyLoanAccountRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LegacyLoanAccountRepository repository;

    @BeforeEach
    void setUp() {
        entityManager.getEntityManager()
                .createNativeQuery("DELETE FROM CDW_LN_ACCT")
                .executeUpdate();

        insertLoan("LN-001", "B-001", "99.50", "50000", "3.250", "0");
        insertLoan("LN-002", "B-002", "1000.00", "200000", "5.750", "15");
        insertLoan("LN-003", "B-003", "250.75", "85000", "4.125", "30");
        insertLoan("LN-004", "B-004", "10500.00", "350000", "2.990", "0");
        insertLoan("LN-005", "B-005", "5.99", "1500000", "6.000", "45");
    }

    @Test
    void orderByCurrentBalanceDesc_usesNumericSort() {
        List<LegacyLoanAccount> results = repository.findAllOrderByCurrentBalanceDesc();

        assertEquals(5, results.size());
        assertEquals("LN-004", results.get(0).getLoanAccountNumber()); // 10500.00
        assertEquals("LN-002", results.get(1).getLoanAccountNumber()); // 1000.00
        assertEquals("LN-003", results.get(2).getLoanAccountNumber()); // 250.75
        assertEquals("LN-001", results.get(3).getLoanAccountNumber()); // 99.50
        assertEquals("LN-005", results.get(4).getLoanAccountNumber()); // 5.99
    }

    @Test
    void orderByCurrentBalanceAsc_usesNumericSort() {
        List<LegacyLoanAccount> results = repository.findAllOrderByCurrentBalanceAsc();

        assertEquals(5, results.size());
        assertEquals("LN-005", results.get(0).getLoanAccountNumber()); // 5.99
        assertEquals("LN-001", results.get(1).getLoanAccountNumber()); // 99.50
        assertEquals("LN-003", results.get(2).getLoanAccountNumber()); // 250.75
        assertEquals("LN-002", results.get(3).getLoanAccountNumber()); // 1000.00
        assertEquals("LN-004", results.get(4).getLoanAccountNumber()); // 10500.00
    }

    @Test
    void findByCurrentBalanceGreaterThan_usesNumericComparison() {
        List<LegacyLoanAccount> results = repository.findByCurrentBalanceGreaterThan(100.0);

        assertEquals(3, results.size());
        assertEquals("LN-003", results.get(0).getLoanAccountNumber()); // 250.75
        assertEquals("LN-002", results.get(1).getLoanAccountNumber()); // 1000.00
        assertEquals("LN-004", results.get(2).getLoanAccountNumber()); // 10500.00
    }

    @Test
    void findByCurrentBalanceLessThan_usesNumericComparison() {
        List<LegacyLoanAccount> results = repository.findByCurrentBalanceLessThan(200.0);

        assertEquals(2, results.size());
        assertEquals("LN-001", results.get(0).getLoanAccountNumber()); // 99.50
        assertEquals("LN-005", results.get(1).getLoanAccountNumber()); // 5.99
    }

    @Test
    void orderByOriginalAmountDesc_usesNumericSort() {
        List<LegacyLoanAccount> results = repository.findAllOrderByOriginalAmountDesc();

        assertEquals(5, results.size());
        assertEquals("LN-005", results.get(0).getLoanAccountNumber()); // 1500000
        assertEquals("LN-004", results.get(1).getLoanAccountNumber()); // 350000
        assertEquals("LN-002", results.get(2).getLoanAccountNumber()); // 200000
        assertEquals("LN-003", results.get(3).getLoanAccountNumber()); // 85000
        assertEquals("LN-001", results.get(4).getLoanAccountNumber()); // 50000
    }

    @Test
    void orderByInterestRateDesc_usesNumericSort() {
        List<LegacyLoanAccount> results = repository.findAllOrderByInterestRateDesc();

        assertEquals(5, results.size());
        assertEquals("LN-005", results.get(0).getLoanAccountNumber()); // 6.000
        assertEquals("LN-002", results.get(1).getLoanAccountNumber()); // 5.750
        assertEquals("LN-003", results.get(2).getLoanAccountNumber()); // 4.125
        assertEquals("LN-001", results.get(3).getLoanAccountNumber()); // 3.250
        assertEquals("LN-004", results.get(4).getLoanAccountNumber()); // 2.990
    }

    @Test
    void orderByDelinquencyDaysDesc_usesNumericSort() {
        List<LegacyLoanAccount> results = repository.findAllOrderByDelinquencyDaysDesc();

        assertEquals(5, results.size());
        assertEquals("LN-005", results.get(0).getLoanAccountNumber()); // 45
        assertEquals("LN-003", results.get(1).getLoanAccountNumber()); // 30
        assertEquals("LN-002", results.get(2).getLoanAccountNumber()); // 15
        // LN-001 and LN-004 both have 0 — order between them is unspecified
        List<String> lastTwo = List.of(
                results.get(3).getLoanAccountNumber(),
                results.get(4).getLoanAccountNumber());
        assertTrue(lastTwo.contains("LN-001"));
        assertTrue(lastTwo.contains("LN-004"));
    }

    @Test
    void stringSort_wouldProduceWrongBalanceOrder() {
        List<LegacyLoanAccount> all = repository.findAllOrderByCurrentBalanceAsc();
        BigDecimal prev = BigDecimal.ZERO;
        for (LegacyLoanAccount loan : all) {
            BigDecimal balance = new BigDecimal(loan.getCurrentBalance().replace(",", ""));
            assertTrue(balance.compareTo(prev) >= 0,
                    "Balance " + balance + " should be >= " + prev);
            prev = balance;
        }
    }

    private void insertLoan(String loanId, String borrowerId, String balance,
                            String origAmount, String rate, String dlqDays) {
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO CDW_LN_ACCT " +
                        "(LN_ACCT_NBR, BORR_ID, BORR_FST_NM, BORR_LST_NM, BORR_SSN_LST4, " +
                        "PROD_CD, LN_ORIG_AMT, LN_CURR_BAL, LN_INT_RT, LN_TERM_MOS, " +
                        "LN_PMT_AMT, LN_ORIG_DT, LN_MAT_DT, LN_1ST_PMT_DT, LN_NXT_PMT_DT, " +
                        "LN_STAT_CD, LN_DLQ_DAYS, LN_ESCROW_BAL, LN_LTV_PCT, " +
                        "PROP_ADDR_LN1, PROP_CTY_NM, PROP_ST_CD, PROP_ZIP_CD, PROP_TYP_CD, " +
                        "PROP_APRS_VAL, LN_CRET_DT, LN_UPDT_DT) VALUES " +
                        "(?1, ?2, 'Test', 'User', '1234', 'FXD30', ?3, ?4, ?5, '360', " +
                        "'1000.00', '01/01/2020', '01/01/2050', '02/01/2020', '01/01/2026', " +
                        "'ACT', ?6, '0.00', '80.0', '123 Test St', 'TestCity', 'TX', '12345', " +
                        "'SFR', '200000', '01/01/2020', '01/01/2025')")
                .setParameter(1, loanId)
                .setParameter(2, borrowerId)
                .setParameter(3, origAmount)
                .setParameter(4, balance)
                .setParameter(5, rate)
                .setParameter(6, dlqDays)
                .executeUpdate();
    }
}
