package com.workshop.loanservice.validation.report;

import com.workshop.loanservice.validation.ValidationIssue;

import java.util.List;

/**
 * Quality score for a single legacy record.
 *
 * @param table    legacy table the record belongs to
 * @param recordId primary key of the record
 * @param score    quality score in {@code [0, 100]} (100 = no issues)
 * @param issues   issues found on the record
 */
public record RecordQualityScore(
        String table,
        String recordId,
        int score,
        List<ValidationIssue> issues) {

    private static final int PERFECT_SCORE = 100;

    /** Build a score from issues, deducting each issue's severity penalty (floored at 0). */
    public static RecordQualityScore of(String table, String recordId, List<ValidationIssue> issues) {
        int deduction = issues.stream().mapToInt(i -> i.severity().getPenalty()).sum();
        int score = Math.max(0, PERFECT_SCORE - deduction);
        return new RecordQualityScore(table, recordId, score, issues);
    }

    public boolean isClean() {
        return issues.isEmpty();
    }
}
