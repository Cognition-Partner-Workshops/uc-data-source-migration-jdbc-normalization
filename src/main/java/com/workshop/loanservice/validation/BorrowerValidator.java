package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyBorrower;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Data quality rules for {@code CDW_BORR_MSTR} (borrower master).
 */
@Component
public class BorrowerValidator implements RecordValidator<LegacyBorrower> {

    private static final String TABLE = "CDW_BORR_MSTR";
    private static final int MIN_CREDIT_SCORE = 300;
    private static final int MAX_CREDIT_SCORE = 850;

    @Override
    public String tableName() {
        return TABLE;
    }

    @Override
    public String recordId(LegacyBorrower borrower) {
        return borrower.getBorrowerId();
    }

    @Override
    public List<ValidationIssue> validate(LegacyBorrower b, ReferenceData refs) {
        List<ValidationIssue> issues = new ArrayList<>();
        String id = b.getBorrowerId();

        // Required fields.
        if (LegacyValueChecks.isBlank(b.getBorrowerId())) {
            issues.add(ValidationIssue.error(TABLE, id, "BORR_ID", "BORR_ID_REQUIRED",
                    "Borrower id is missing"));
        }
        if (LegacyValueChecks.isBlank(b.getFirstName())) {
            issues.add(ValidationIssue.error(TABLE, id, "BORR_FST_NM", "BORR_FST_NM_REQUIRED",
                    "First name is missing"));
        }
        if (LegacyValueChecks.isBlank(b.getLastName())) {
            issues.add(ValidationIssue.error(TABLE, id, "BORR_LST_NM", "BORR_LST_NM_REQUIRED",
                    "Last name is missing"));
        }

        // Date fields (MM/DD/YYYY).
        checkDate(issues, id, "BORR_DOB_DT", "BORR_DOB_DT_FORMAT", b.getDateOfBirth(), true);
        checkDate(issues, id, "BORR_CRET_DT", "BORR_CRET_DT_FORMAT", b.getCreatedDate(), false);
        checkDate(issues, id, "BORR_UPDT_DT", "BORR_UPDT_DT_FORMAT", b.getUpdatedDate(), false);

        // Credit score: must be an integer in a plausible range.
        String score = b.getCreditScore();
        if (LegacyValueChecks.isBlank(score)) {
            issues.add(ValidationIssue.warning(TABLE, id, "BORR_CRDT_SCR", "BORR_CRDT_SCR_PRESENT",
                    "Credit score is missing"));
        } else if (!LegacyValueChecks.isParseableInteger(score)) {
            issues.add(ValidationIssue.error(TABLE, id, "BORR_CRDT_SCR", "BORR_CRDT_SCR_NUMERIC",
                    "Credit score is not an integer: '" + score + "'"));
        } else {
            int value = Integer.parseInt(score.trim());
            if (value < MIN_CREDIT_SCORE || value > MAX_CREDIT_SCORE) {
                issues.add(ValidationIssue.warning(TABLE, id, "BORR_CRDT_SCR", "BORR_CRDT_SCR_RANGE",
                        "Credit score " + value + " outside plausible range "
                                + MIN_CREDIT_SCORE + "-" + MAX_CREDIT_SCORE));
            }
        }

        // Annual income: comma-grouped amount.
        if (LegacyValueChecks.isPresent(b.getAnnualIncome())
                && !LegacyValueChecks.isParseableAmount(b.getAnnualIncome())) {
            issues.add(ValidationIssue.error(TABLE, id, "BORR_ANN_INCM", "BORR_ANN_INCM_NUMERIC",
                    "Annual income is not parseable: '" + b.getAnnualIncome() + "'"));
        }

        // Status code domain.
        if (LegacyValueChecks.isBlank(b.getStatusCode())) {
            issues.add(ValidationIssue.error(TABLE, id, "BORR_STAT_CD", "BORR_STAT_CD_REQUIRED",
                    "Status code is missing"));
        } else if (!LegacyValueChecks.BORROWER_STATUS_CODES.contains(b.getStatusCode().trim())) {
            issues.add(ValidationIssue.warning(TABLE, id, "BORR_STAT_CD", "BORR_STAT_CD_VALID",
                    "Unknown borrower status code '" + b.getStatusCode()
                            + "'; expected one of " + LegacyValueChecks.BORROWER_STATUS_CODES));
        }

        // Email format (optional field).
        if (LegacyValueChecks.isPresent(b.getEmail()) && !LegacyValueChecks.isValidEmail(b.getEmail())) {
            issues.add(ValidationIssue.warning(TABLE, id, "BORR_EMAIL_ADDR", "BORR_EMAIL_ADDR_FORMAT",
                    "Email is malformed: '" + b.getEmail() + "'"));
        }

        return issues;
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
