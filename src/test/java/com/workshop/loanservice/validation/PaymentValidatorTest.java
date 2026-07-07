package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyPayment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentValidatorTest {

    private final PaymentValidator validator = new PaymentValidator();
    private final ReferenceData refs = new ReferenceData(Set.of(), Set.of(), Set.of("LN-2019-00142"));

    private LegacyPayment validPayment() {
        LegacyPayment p = new LegacyPayment();
        p.setPaymentSequenceNumber("PMT-2025120001");
        p.setLoanAccountNumber("LN-2019-00142");
        p.setPaymentDate("12/15/2025");
        p.setTotalAmount("1,487.02");
        p.setPrincipalAmount("456.78");
        p.setInterestAmount("1,074.69");
        p.setEscrowAmount("355.55");
        p.setLateFee("0.00");
        p.setTypeCode("REG");
        p.setStatusCode("PST");
        p.setReceivedDate("12/14/2025");
        p.setProcessedDate("12/15/2025");
        p.setCreatedDate("12/15/2025");
        p.setUpdatedDate("12/15/2025");
        return p;
    }

    private Set<String> ruleIds(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::ruleId).collect(Collectors.toSet());
    }

    @Test
    void cleanPaymentHasNoIssues() {
        assertTrue(validator.validate(validPayment(), refs).isEmpty());
    }

    @Test
    void orphanLoanAccountRaisesReferentialIntegrityError() {
        LegacyPayment p = validPayment();
        p.setLoanAccountNumber("LN-0000-00000");
        assertTrue(validator.validate(p, refs).stream()
                .anyMatch(i -> i.ruleId().equals("PMT_LN_ACCT_NBR_FK") && i.severity() == Severity.ERROR));
    }

    @Test
    void invalidTypeAndStatusCodesRaiseErrors() {
        LegacyPayment p = validPayment();
        p.setTypeCode("ZZZ");
        p.setStatusCode("QQQ");
        assertTrue(ruleIds(validator.validate(p, refs)).containsAll(Set.of("PMT_TYP_CD_VALID", "PMT_STAT_CD_VALID")));
    }

    @Test
    void missingPaymentDateRaisesError() {
        LegacyPayment p = validPayment();
        p.setPaymentDate(null);
        assertTrue(ruleIds(validator.validate(p, refs)).contains("PMT_DT_FORMAT"));
    }

    @Test
    void unparseableAmountRaisesError() {
        LegacyPayment p = validPayment();
        p.setTotalAmount("1,4a7.02");
        assertTrue(ruleIds(validator.validate(p, refs)).contains("PMT_AMT_NUMERIC"));
    }
}
