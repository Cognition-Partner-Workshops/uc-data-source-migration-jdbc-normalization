package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanProductValidatorTest {

    private final LoanProductValidator validator = new LoanProductValidator();

    private LegacyLoanProduct validProduct() {
        LegacyLoanProduct p = new LegacyLoanProduct();
        p.setProductCode("FXD30");
        p.setDescription("30-Year Fixed Rate Mortgage");
        p.setTermMonths("360");
        p.setMinAmount("50,000");
        p.setMaxAmount("1,500,000");
        p.setStatusCode("ACT");
        p.setEffectiveDate("01/01/2020");
        p.setExpirationDate("12/31/2099");
        return p;
    }

    private Set<String> ruleIds(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::ruleId).collect(Collectors.toSet());
    }

    @Test
    void cleanProductHasNoIssues() {
        assertTrue(validator.validate(validProduct(), ReferenceData.empty()).isEmpty());
    }

    @Test
    void missingCodeAndDescriptionRaiseErrors() {
        LegacyLoanProduct p = validProduct();
        p.setProductCode(null);
        p.setDescription("");
        assertTrue(ruleIds(validator.validate(p, ReferenceData.empty()))
                .containsAll(Set.of("PROD_CD_REQUIRED", "PROD_DESC_TXT_REQUIRED")));
    }

    @Test
    void minGreaterThanMaxRaisesWarning() {
        LegacyLoanProduct p = validProduct();
        p.setMinAmount("2,000,000");
        assertTrue(validator.validate(p, ReferenceData.empty()).stream()
                .anyMatch(i -> i.ruleId().equals("PROD_AMT_RANGE") && i.severity() == Severity.WARNING));
    }

    @Test
    void nonNumericTermRaisesError() {
        LegacyLoanProduct p = validProduct();
        p.setTermMonths("thirty years");
        assertTrue(ruleIds(validator.validate(p, ReferenceData.empty())).contains("PROD_TERM_MOS_NUMERIC"));
    }
}
