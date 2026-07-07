package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyBorrower;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorrowerValidatorTest {

    private final BorrowerValidator validator = new BorrowerValidator();

    private LegacyBorrower validBorrower() {
        LegacyBorrower b = new LegacyBorrower();
        b.setBorrowerId("B-10001");
        b.setFirstName("James");
        b.setLastName("Mitchell");
        b.setDateOfBirth("03/15/1978");
        b.setCreatedDate("01/15/2019");
        b.setUpdatedDate("11/03/2025");
        b.setCreditScore("745");
        b.setAnnualIncome("92,500");
        b.setStatusCode("ACT");
        b.setEmail("j.mitchell@email.com");
        return b;
    }

    private Set<String> ruleIds(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::ruleId).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void cleanBorrowerHasNoIssues() {
        assertTrue(validator.validate(validBorrower(), ReferenceData.empty()).isEmpty());
    }

    @Test
    void missingRequiredFieldsRaiseErrors() {
        LegacyBorrower b = validBorrower();
        b.setFirstName(null);
        b.setLastName("  ");
        List<ValidationIssue> issues = validator.validate(b, ReferenceData.empty());
        assertTrue(ruleIds(issues).containsAll(Set.of("BORR_FST_NM_REQUIRED", "BORR_LST_NM_REQUIRED")));
    }

    @Test
    void invalidDobFormatRaisesError() {
        LegacyBorrower b = validBorrower();
        b.setDateOfBirth("1978-03-15");
        List<ValidationIssue> issues = validator.validate(b, ReferenceData.empty());
        assertTrue(ruleIds(issues).contains("BORR_DOB_DT_FORMAT"));
    }

    @Test
    void nonNumericCreditScoreRaisesError() {
        LegacyBorrower b = validBorrower();
        b.setCreditScore("EXCELLENT");
        assertTrue(ruleIds(validator.validate(b, ReferenceData.empty())).contains("BORR_CRDT_SCR_NUMERIC"));
    }

    @Test
    void outOfRangeCreditScoreRaisesWarning() {
        LegacyBorrower b = validBorrower();
        b.setCreditScore("1200");
        List<ValidationIssue> issues = validator.validate(b, ReferenceData.empty());
        assertTrue(issues.stream().anyMatch(i -> i.ruleId().equals("BORR_CRDT_SCR_RANGE")
                && i.severity() == Severity.WARNING));
    }

    @Test
    void unparseableIncomeRaisesError() {
        LegacyBorrower b = validBorrower();
        b.setAnnualIncome("ninety thousand");
        assertTrue(ruleIds(validator.validate(b, ReferenceData.empty())).contains("BORR_ANN_INCM_NUMERIC"));
    }

    @Test
    void unknownStatusCodeRaisesWarning() {
        LegacyBorrower b = validBorrower();
        b.setStatusCode("XYZ");
        List<ValidationIssue> issues = validator.validate(b, ReferenceData.empty());
        assertEquals(Severity.WARNING,
                issues.stream().filter(i -> i.ruleId().equals("BORR_STAT_CD_VALID"))
                        .findFirst().orElseThrow().severity());
    }
}
