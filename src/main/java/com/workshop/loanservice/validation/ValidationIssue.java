package com.workshop.loanservice.validation;

/**
 * A single data quality finding raised by a {@link RecordValidator}.
 *
 * @param table     legacy table the record belongs to (e.g. {@code CDW_LN_ACCT})
 * @param recordId  primary key of the offending record
 * @param field     legacy column the issue relates to ({@code "*"} for record-level issues)
 * @param ruleId    stable identifier of the rule that fired (e.g. {@code LN_STAT_CD_VALID})
 * @param severity  how serious the issue is
 * @param message   human-readable explanation
 */
public record ValidationIssue(
        String table,
        String recordId,
        String field,
        String ruleId,
        Severity severity,
        String message) {

    public static ValidationIssue error(String table, String recordId, String field, String ruleId, String message) {
        return new ValidationIssue(table, recordId, field, ruleId, Severity.ERROR, message);
    }

    public static ValidationIssue warning(String table, String recordId, String field, String ruleId, String message) {
        return new ValidationIssue(table, recordId, field, ruleId, Severity.WARNING, message);
    }

    public static ValidationIssue info(String table, String recordId, String field, String ruleId, String message) {
        return new ValidationIssue(table, recordId, field, ruleId, Severity.INFO, message);
    }
}
