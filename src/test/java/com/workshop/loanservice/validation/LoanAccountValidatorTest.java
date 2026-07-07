package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanAccountValidatorTest {

    private final LoanAccountValidator validator = new LoanAccountValidator();

    private final ReferenceData refs = new ReferenceData(
            Set.of("B-10001"), Set.of("FXD30"), Set.of("LN-2019-00142"));

    private LegacyLoanAccount validAccount() {
        LegacyLoanAccount a = new LegacyLoanAccount();
        a.setLoanAccountNumber("LN-2019-00142");
        a.setBorrowerId("B-10001");
        a.setProductCode("FXD30");
        a.setOriginalAmount("285,000");
        a.setCurrentBalance("271,432.56");
        a.setInterestRate("4.750");
        a.setTermMonths("360");
        a.setMonthlyPayment("1,487.02");
        a.setOriginationDate("02/15/2019");
        a.setMaturityDate("02/15/2049");
        a.setFirstPaymentDate("03/15/2019");
        a.setNextPaymentDate("01/15/2026");
        a.setStatusCode("ACT");
        a.setDelinquencyDays("0");
        a.setEscrowBalance("3,245.80");
        a.setLtvPercent("82.5");
        a.setPropertyType("SFR");
        a.setAppraisedValue("345,000");
        a.setCreatedDate("02/01/2019");
        a.setUpdatedDate("12/01/2025");
        return a;
    }

    private Set<String> ruleIds(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::ruleId).collect(Collectors.toSet());
    }

    @Test
    void cleanAccountHasNoIssues() {
        assertTrue(validator.validate(validAccount(), refs).isEmpty());
    }

    @Test
    void orphanBorrowerIdRaisesReferentialIntegrityError() {
        LegacyLoanAccount a = validAccount();
        a.setBorrowerId("B-99999");
        List<ValidationIssue> issues = validator.validate(a, refs);
        assertTrue(issues.stream().anyMatch(i -> i.ruleId().equals("LN_BORR_ID_FK")
                && i.severity() == Severity.ERROR));
    }

    @Test
    void orphanProductCodeRaisesReferentialIntegrityError() {
        LegacyLoanAccount a = validAccount();
        a.setProductCode("ZZZ99");
        assertTrue(ruleIds(validator.validate(a, refs)).contains("LN_PROD_CD_FK"));
    }

    @Test
    void invalidStatusCodeRaisesError() {
        LegacyLoanAccount a = validAccount();
        a.setStatusCode("PENDING");
        assertTrue(ruleIds(validator.validate(a, refs)).contains("LN_STAT_CD_VALID"));
    }

    @Test
    void unparseableAmountRaisesError() {
        LegacyLoanAccount a = validAccount();
        a.setCurrentBalance("~271432");
        assertTrue(ruleIds(validator.validate(a, refs)).contains("LN_CURR_BAL_NUMERIC"));
    }

    @Test
    void malformedDateRaisesError() {
        LegacyLoanAccount a = validAccount();
        a.setMaturityDate("02/30/2049");
        assertTrue(ruleIds(validator.validate(a, refs)).contains("LN_MAT_DT_FORMAT"));
    }

    @Test
    void unknownPropertyTypeRaisesWarning() {
        LegacyLoanAccount a = validAccount();
        a.setPropertyType("XXX");
        assertTrue(validator.validate(a, refs).stream()
                .anyMatch(i -> i.ruleId().equals("PROP_TYP_CD_VALID") && i.severity() == Severity.WARNING));
    }
}
