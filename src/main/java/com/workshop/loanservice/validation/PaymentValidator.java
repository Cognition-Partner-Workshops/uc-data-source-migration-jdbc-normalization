package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyPayment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Data quality rules for {@code CDW_PMT_HIST} (payment history).
 *
 * <p>Includes a referential-integrity check that {@code LN_ACCT_NBR} resolves
 * to a row in {@code CDW_LN_ACCT}.
 */
@Component
public class PaymentValidator implements RecordValidator<LegacyPayment> {

    private static final String TABLE = "CDW_PMT_HIST";

    @Override
    public String tableName() {
        return TABLE;
    }

    @Override
    public String recordId(LegacyPayment payment) {
        return payment.getPaymentSequenceNumber();
    }

    @Override
    public List<ValidationIssue> validate(LegacyPayment p, ReferenceData refs) {
        List<ValidationIssue> issues = new ArrayList<>();
        String id = p.getPaymentSequenceNumber();

        if (LegacyValueChecks.isBlank(p.getPaymentSequenceNumber())) {
            issues.add(ValidationIssue.error(TABLE, id, "PMT_SEQ_NBR", "PMT_SEQ_NBR_REQUIRED",
                    "Payment sequence number is missing"));
        }
        if (LegacyValueChecks.isBlank(p.getLoanAccountNumber())) {
            issues.add(ValidationIssue.error(TABLE, id, "LN_ACCT_NBR", "PMT_LN_ACCT_NBR_REQUIRED",
                    "Loan account number is missing"));
        } else if (!refs.loanAccountNumbers().contains(p.getLoanAccountNumber().trim())) {
            issues.add(ValidationIssue.error(TABLE, id, "LN_ACCT_NBR", "PMT_LN_ACCT_NBR_FK",
                    "Loan account number '" + p.getLoanAccountNumber()
                            + "' has no matching row in CDW_LN_ACCT"));
        }

        // Amounts.
        checkAmount(issues, id, "PMT_AMT", "PMT_AMT_NUMERIC", p.getTotalAmount());
        checkAmount(issues, id, "PMT_PRIN_AMT", "PMT_PRIN_AMT_NUMERIC", p.getPrincipalAmount());
        checkAmount(issues, id, "PMT_INT_AMT", "PMT_INT_AMT_NUMERIC", p.getInterestAmount());
        checkAmount(issues, id, "PMT_ESCROW_AMT", "PMT_ESCROW_AMT_NUMERIC", p.getEscrowAmount());
        checkAmount(issues, id, "PMT_LATE_FEE", "PMT_LATE_FEE_NUMERIC", p.getLateFee());

        // Dates.
        checkDate(issues, id, "PMT_DT", "PMT_DT_FORMAT", p.getPaymentDate(), true);
        checkDate(issues, id, "PMT_RECV_DT", "PMT_RECV_DT_FORMAT", p.getReceivedDate(), false);
        checkDate(issues, id, "PMT_PROC_DT", "PMT_PROC_DT_FORMAT", p.getProcessedDate(), false);
        checkDate(issues, id, "PMT_CRET_DT", "PMT_CRET_DT_FORMAT", p.getCreatedDate(), false);
        checkDate(issues, id, "PMT_UPDT_DT", "PMT_UPDT_DT_FORMAT", p.getUpdatedDate(), false);

        // Code domains.
        if (LegacyValueChecks.isBlank(p.getTypeCode())) {
            issues.add(ValidationIssue.error(TABLE, id, "PMT_TYP_CD", "PMT_TYP_CD_REQUIRED",
                    "Payment type code is missing"));
        } else if (!LegacyValueChecks.PAYMENT_TYPE_CODES.contains(p.getTypeCode().trim())) {
            issues.add(ValidationIssue.error(TABLE, id, "PMT_TYP_CD", "PMT_TYP_CD_VALID",
                    "Invalid payment type code '" + p.getTypeCode()
                            + "'; expected one of " + LegacyValueChecks.PAYMENT_TYPE_CODES));
        }
        if (LegacyValueChecks.isBlank(p.getStatusCode())) {
            issues.add(ValidationIssue.error(TABLE, id, "PMT_STAT_CD", "PMT_STAT_CD_REQUIRED",
                    "Payment status code is missing"));
        } else if (!LegacyValueChecks.PAYMENT_STATUS_CODES.contains(p.getStatusCode().trim())) {
            issues.add(ValidationIssue.error(TABLE, id, "PMT_STAT_CD", "PMT_STAT_CD_VALID",
                    "Invalid payment status code '" + p.getStatusCode()
                            + "'; expected one of " + LegacyValueChecks.PAYMENT_STATUS_CODES));
        }

        return issues;
    }

    private void checkAmount(List<ValidationIssue> issues, String id, String field, String ruleId, String value) {
        if (LegacyValueChecks.isPresent(value) && !LegacyValueChecks.isParseableAmount(value)) {
            issues.add(ValidationIssue.error(TABLE, id, field, ruleId,
                    field + " is not a parseable amount: '" + value + "'"));
        }
    }

    private void checkDate(List<ValidationIssue> issues, String id, String field, String ruleId,
                           String value, boolean required) {
        if (LegacyValueChecks.isBlank(value)) {
            if (required) {
                issues.add(ValidationIssue.error(TABLE, id, field, ruleId, field + " is missing"));
            }
            return;
        }
        if (!LegacyValueChecks.isValidDate(value)) {
            issues.add(ValidationIssue.error(TABLE, id, field, ruleId,
                    field + " is not a valid MM/DD/YYYY date: '" + value + "'"));
        }
    }
}
