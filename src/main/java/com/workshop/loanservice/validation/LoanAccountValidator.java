package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Data quality rules for {@code CDW_LN_ACCT} (denormalized loan accounts).
 *
 * <p>Includes referential-integrity checks against {@code CDW_BORR_MSTR}
 * (via {@code BORR_ID}) and {@code CDW_LN_PROD} (via {@code PROD_CD}), since
 * the legacy warehouse enforces no foreign keys.
 */
@Component
public class LoanAccountValidator implements RecordValidator<LegacyLoanAccount> {

    private static final String TABLE = "CDW_LN_ACCT";

    @Override
    public String tableName() {
        return TABLE;
    }

    @Override
    public String recordId(LegacyLoanAccount account) {
        return account.getLoanAccountNumber();
    }

    @Override
    public List<ValidationIssue> validate(LegacyLoanAccount a, ReferenceData refs) {
        List<ValidationIssue> issues = new ArrayList<>();
        String id = a.getLoanAccountNumber();

        // Required identifiers.
        if (LegacyValueChecks.isBlank(a.getLoanAccountNumber())) {
            issues.add(ValidationIssue.error(TABLE, id, "LN_ACCT_NBR", "LN_ACCT_NBR_REQUIRED",
                    "Loan account number is missing"));
        }
        if (LegacyValueChecks.isBlank(a.getBorrowerId())) {
            issues.add(ValidationIssue.error(TABLE, id, "BORR_ID", "LN_BORR_ID_REQUIRED",
                    "Borrower id is missing"));
        }
        if (LegacyValueChecks.isBlank(a.getProductCode())) {
            issues.add(ValidationIssue.error(TABLE, id, "PROD_CD", "LN_PROD_CD_REQUIRED",
                    "Product code is missing"));
        }

        // Referential integrity: BORR_ID must resolve to a borrower master record.
        if (LegacyValueChecks.isPresent(a.getBorrowerId())
                && !refs.borrowerIds().contains(a.getBorrowerId().trim())) {
            issues.add(ValidationIssue.error(TABLE, id, "BORR_ID", "LN_BORR_ID_FK",
                    "Borrower id '" + a.getBorrowerId()
                            + "' has no matching row in CDW_BORR_MSTR"));
        }

        // Referential integrity: PROD_CD must resolve to a product.
        if (LegacyValueChecks.isPresent(a.getProductCode())
                && !refs.productCodes().contains(a.getProductCode().trim())) {
            issues.add(ValidationIssue.error(TABLE, id, "PROD_CD", "LN_PROD_CD_FK",
                    "Product code '" + a.getProductCode()
                            + "' has no matching row in CDW_LN_PROD"));
        }

        // Amounts.
        checkAmount(issues, id, "LN_ORIG_AMT", "LN_ORIG_AMT_NUMERIC", a.getOriginalAmount());
        checkAmount(issues, id, "LN_CURR_BAL", "LN_CURR_BAL_NUMERIC", a.getCurrentBalance());
        checkAmount(issues, id, "LN_PMT_AMT", "LN_PMT_AMT_NUMERIC", a.getMonthlyPayment());
        checkAmount(issues, id, "LN_ESCROW_BAL", "LN_ESCROW_BAL_NUMERIC", a.getEscrowBalance());
        checkAmount(issues, id, "PROP_APRS_VAL", "PROP_APRS_VAL_NUMERIC", a.getAppraisedValue());

        // Plain decimals.
        checkDecimal(issues, id, "LN_INT_RT", "LN_INT_RT_NUMERIC", a.getInterestRate());
        checkDecimal(issues, id, "LN_LTV_PCT", "LN_LTV_PCT_NUMERIC", a.getLtvPercent());

        // Integers.
        checkInteger(issues, id, "LN_TERM_MOS", "LN_TERM_MOS_NUMERIC", a.getTermMonths());
        checkInteger(issues, id, "LN_DLQ_DAYS", "LN_DLQ_DAYS_NUMERIC", a.getDelinquencyDays());

        // Dates.
        checkDate(issues, id, "LN_ORIG_DT", "LN_ORIG_DT_FORMAT", a.getOriginationDate());
        checkDate(issues, id, "LN_MAT_DT", "LN_MAT_DT_FORMAT", a.getMaturityDate());
        checkDate(issues, id, "LN_1ST_PMT_DT", "LN_1ST_PMT_DT_FORMAT", a.getFirstPaymentDate());
        checkDate(issues, id, "LN_NXT_PMT_DT", "LN_NXT_PMT_DT_FORMAT", a.getNextPaymentDate());
        checkDate(issues, id, "LN_CRET_DT", "LN_CRET_DT_FORMAT", a.getCreatedDate());
        checkDate(issues, id, "LN_UPDT_DT", "LN_UPDT_DT_FORMAT", a.getUpdatedDate());

        // Status code domain.
        if (LegacyValueChecks.isBlank(a.getStatusCode())) {
            issues.add(ValidationIssue.error(TABLE, id, "LN_STAT_CD", "LN_STAT_CD_REQUIRED",
                    "Loan status code is missing"));
        } else if (!LegacyValueChecks.LOAN_STATUS_CODES.contains(a.getStatusCode().trim())) {
            issues.add(ValidationIssue.error(TABLE, id, "LN_STAT_CD", "LN_STAT_CD_VALID",
                    "Invalid loan status code '" + a.getStatusCode()
                            + "'; expected one of " + LegacyValueChecks.LOAN_STATUS_CODES));
        }

        // Property type domain (optional expansion).
        if (LegacyValueChecks.isPresent(a.getPropertyType())
                && !LegacyValueChecks.PROPERTY_TYPE_CODES.contains(a.getPropertyType().trim())) {
            issues.add(ValidationIssue.warning(TABLE, id, "PROP_TYP_CD", "PROP_TYP_CD_VALID",
                    "Unknown property type code '" + a.getPropertyType()
                            + "'; expected one of " + LegacyValueChecks.PROPERTY_TYPE_CODES));
        }

        return issues;
    }

    private void checkAmount(List<ValidationIssue> issues, String id, String field, String ruleId, String value) {
        if (LegacyValueChecks.isPresent(value) && !LegacyValueChecks.isParseableAmount(value)) {
            issues.add(ValidationIssue.error(TABLE, id, field, ruleId,
                    field + " is not a parseable amount: '" + value + "'"));
        }
    }

    private void checkDecimal(List<ValidationIssue> issues, String id, String field, String ruleId, String value) {
        if (LegacyValueChecks.isPresent(value) && !LegacyValueChecks.isParseableDecimal(value)) {
            issues.add(ValidationIssue.error(TABLE, id, field, ruleId,
                    field + " is not a parseable decimal: '" + value + "'"));
        }
    }

    private void checkInteger(List<ValidationIssue> issues, String id, String field, String ruleId, String value) {
        if (LegacyValueChecks.isPresent(value) && !LegacyValueChecks.isParseableInteger(value)) {
            issues.add(ValidationIssue.error(TABLE, id, field, ruleId,
                    field + " is not an integer: '" + value + "'"));
        }
    }

    private void checkDate(List<ValidationIssue> issues, String id, String field, String ruleId, String value) {
        if (LegacyValueChecks.isPresent(value) && !LegacyValueChecks.isValidDate(value)) {
            issues.add(ValidationIssue.error(TABLE, id, field, ruleId,
                    field + " is not a valid MM/DD/YYYY date: '" + value + "'"));
        }
    }
}
