package com.workshop.loanservice.validation;

/**
 * Severity of a data quality {@link ValidationIssue}.
 *
 * <p>Each severity carries a score penalty that the
 * {@link com.workshop.loanservice.validation.report.DataQualityService}
 * subtracts from a record's perfect score of 100.
 */
public enum Severity {

    /** A hard data-integrity failure (missing required field, unparseable amount, orphaned FK). */
    ERROR(25),

    /** A suspicious value that is usable but likely wrong (out-of-range credit score, unknown code). */
    WARNING(10),

    /** A minor observation (denormalized copy drift, optional field missing). */
    INFO(2);

    private final int penalty;

    Severity(int penalty) {
        this.penalty = penalty;
    }

    /** Points deducted from a record's quality score per issue of this severity. */
    public int getPenalty() {
        return penalty;
    }
}
