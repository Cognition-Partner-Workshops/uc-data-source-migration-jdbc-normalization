package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyLoanProduct;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Data quality rules for {@code CDW_LN_PROD} (loan product catalog).
 */
@Component
public class LoanProductValidator implements RecordValidator<LegacyLoanProduct> {

    private static final String TABLE = "CDW_LN_PROD";

    @Override
    public String tableName() {
        return TABLE;
    }

    @Override
    public String recordId(LegacyLoanProduct product) {
        return product.getProductCode();
    }

    @Override
    public List<ValidationIssue> validate(LegacyLoanProduct p, ReferenceData refs) {
        List<ValidationIssue> issues = new ArrayList<>();
        String id = p.getProductCode();

        if (LegacyValueChecks.isBlank(p.getProductCode())) {
            issues.add(ValidationIssue.error(TABLE, id, "PROD_CD", "PROD_CD_REQUIRED",
                    "Product code is missing"));
        }
        if (LegacyValueChecks.isBlank(p.getDescription())) {
            issues.add(ValidationIssue.error(TABLE, id, "PROD_DESC_TXT", "PROD_DESC_TXT_REQUIRED",
                    "Product description is missing"));
        }

        if (LegacyValueChecks.isPresent(p.getTermMonths())
                && !LegacyValueChecks.isParseableInteger(p.getTermMonths())) {
            issues.add(ValidationIssue.error(TABLE, id, "PROD_TERM_MOS", "PROD_TERM_MOS_NUMERIC",
                    "Term months is not an integer: '" + p.getTermMonths() + "'"));
        }

        BigDecimal min = checkAmount(issues, id, "PROD_MIN_AMT", "PROD_MIN_AMT_NUMERIC", p.getMinAmount());
        BigDecimal max = checkAmount(issues, id, "PROD_MAX_AMT", "PROD_MAX_AMT_NUMERIC", p.getMaxAmount());
        if (min != null && max != null && min.compareTo(max) > 0) {
            issues.add(ValidationIssue.warning(TABLE, id, "PROD_MIN_AMT", "PROD_AMT_RANGE",
                    "Min amount " + min + " exceeds max amount " + max));
        }

        if (LegacyValueChecks.isPresent(p.getStatusCode())
                && !LegacyValueChecks.PRODUCT_STATUS_CODES.contains(p.getStatusCode().trim())) {
            issues.add(ValidationIssue.warning(TABLE, id, "PROD_STAT_CD", "PROD_STAT_CD_VALID",
                    "Unknown product status code '" + p.getStatusCode()
                            + "'; expected one of " + LegacyValueChecks.PRODUCT_STATUS_CODES));
        }

        checkDate(issues, id, "PROD_EFF_DT", "PROD_EFF_DT_FORMAT", p.getEffectiveDate());
        checkDate(issues, id, "PROD_EXP_DT", "PROD_EXP_DT_FORMAT", p.getExpirationDate());

        return issues;
    }

    private BigDecimal checkAmount(List<ValidationIssue> issues, String id, String field, String ruleId,
                                   String value) {
        if (LegacyValueChecks.isBlank(value)) {
            return null;
        }
        BigDecimal parsed = LegacyValueChecks.parseAmountOrNull(value);
        if (parsed == null) {
            issues.add(ValidationIssue.error(TABLE, id, field, ruleId,
                    field + " is not a parseable amount: '" + value + "'"));
        }
        return parsed;
    }

    private void checkDate(List<ValidationIssue> issues, String id, String field, String ruleId, String value) {
        if (LegacyValueChecks.isPresent(value) && !LegacyValueChecks.isValidDate(value)) {
            issues.add(ValidationIssue.error(TABLE, id, field, ruleId,
                    field + " is not a valid MM/DD/YYYY date: '" + value + "'"));
        }
    }
}
