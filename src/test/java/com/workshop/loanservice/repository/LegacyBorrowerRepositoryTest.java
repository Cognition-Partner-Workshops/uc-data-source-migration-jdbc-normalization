package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyBorrower;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that verify numeric CAST-based ordering and comparison for VARCHAR
 * columns in the legacy CDW_BORR_MSTR table (credit score, annual income).
 */
@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LegacyBorrowerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LegacyBorrowerRepository repository;

    @BeforeEach
    void setUp() {
        entityManager.getEntityManager()
                .createNativeQuery("DELETE FROM CDW_BORR_MSTR")
                .executeUpdate();

        // Credit scores and incomes chosen so string sort differs from numeric sort:
        //   String sort of scores: "690", "72", "780", "81", "95"
        //   Numeric sort:          72, 81, 95, 690, 780
        insertBorrower("B-001", "Alice", "Smith", "780", "125,000");
        insertBorrower("B-002", "Bob", "Jones", "690", "78,000");
        insertBorrower("B-003", "Carol", "Davis", "72", "9,500");
        insertBorrower("B-004", "Dan", "Brown", "810", "145,000");
        insertBorrower("B-005", "Eve", "Wilson", "95", "250,000");
    }

    @Test
    void orderByCreditScoreDesc_usesNumericSort() {
        List<LegacyBorrower> results = repository.findAllOrderByCreditScoreDesc();

        assertEquals(5, results.size());
        assertEquals("B-004", results.get(0).getBorrowerId()); // 810
        assertEquals("B-001", results.get(1).getBorrowerId()); // 780
        assertEquals("B-002", results.get(2).getBorrowerId()); // 690
        assertEquals("B-005", results.get(3).getBorrowerId()); // 95
        assertEquals("B-003", results.get(4).getBorrowerId()); // 72
    }

    @Test
    void findByAnnualIncomeGreaterThan_usesNumericComparison() {
        List<LegacyBorrower> results = repository.findByAnnualIncomeGreaterThan(100000.0);

        assertEquals(3, results.size());
        // Ordered DESC by income: 250000, 145000, 125000
        assertEquals("B-005", results.get(0).getBorrowerId()); // 250,000
        assertEquals("B-004", results.get(1).getBorrowerId()); // 145,000
        assertEquals("B-001", results.get(2).getBorrowerId()); // 125,000
    }

    @Test
    void creditScoreSort_producesCorrectNumericOrder() {
        List<LegacyBorrower> results = repository.findAllOrderByCreditScoreDesc();

        int prev = Integer.MAX_VALUE;
        for (LegacyBorrower borrower : results) {
            int score = Integer.parseInt(borrower.getCreditScore().trim());
            assertTrue(score <= prev,
                    "Credit score " + score + " should be <= " + prev);
            prev = score;
        }
    }

    private void insertBorrower(String id, String firstName, String lastName,
                                String creditScore, String annualIncome) {
        entityManager.getEntityManager()
                .createNativeQuery("INSERT INTO CDW_BORR_MSTR " +
                        "(BORR_ID, BORR_FST_NM, BORR_LST_NM, BORR_MID_INIT, BORR_SSN_ENCR, " +
                        "BORR_DOB_DT, BORR_ADDR_LN1, BORR_ADDR_LN2, BORR_CTY_NM, BORR_ST_CD, " +
                        "BORR_ZIP_CD, BORR_PH_NBR, BORR_EMAIL_ADDR, BORR_CRDT_SCR, " +
                        "BORR_EMP_STAT, BORR_ANN_INCM, BORR_CRET_DT, BORR_UPDT_DT, " +
                        "BORR_STAT_CD, BORR_REC_TYP) VALUES " +
                        "(?1, ?2, ?3, NULL, 'ENC_XXX', '01/01/1980', '123 Test St', NULL, " +
                        "'TestCity', 'TX', '12345', '555-0000', ?1 || '@test.com', ?4, " +
                        "'EMPLOYED', ?5, '01/01/2020', '01/01/2025', 'ACT', 'PRI')")
                .setParameter(1, id)
                .setParameter(2, firstName)
                .setParameter(3, lastName)
                .setParameter(4, creditScore)
                .setParameter(5, annualIncome)
                .executeUpdate();
    }
}
