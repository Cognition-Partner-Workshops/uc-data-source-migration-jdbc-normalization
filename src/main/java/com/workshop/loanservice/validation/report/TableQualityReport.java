package com.workshop.loanservice.validation.report;

import com.workshop.loanservice.validation.Severity;
import com.workshop.loanservice.validation.ValidationIssue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aggregated data quality report for a single legacy table.
 */
public record TableQualityReport(
        String table,
        int totalRecords,
        int cleanRecords,
        double averageScore,
        long errorCount,
        long warningCount,
        long infoCount,
        List<RecordQualityScore> records) {

    /** Build a table report from the per-record scores. */
    public static TableQualityReport of(String table, List<RecordQualityScore> records) {
        Map<Severity, Long> bySeverity = records.stream()
                .flatMap(r -> r.issues().stream())
                .collect(Collectors.groupingBy(ValidationIssue::severity, Collectors.counting()));

        double avg = records.isEmpty() ? 100.0
                : records.stream().mapToInt(RecordQualityScore::score).average().orElse(100.0);
        long clean = records.stream().filter(RecordQualityScore::isClean).count();

        return new TableQualityReport(
                table,
                records.size(),
                (int) clean,
                round(avg),
                bySeverity.getOrDefault(Severity.ERROR, 0L),
                bySeverity.getOrDefault(Severity.WARNING, 0L),
                bySeverity.getOrDefault(Severity.INFO, 0L),
                records);
    }

    /** All issues across every record in this table. */
    public List<ValidationIssue> allIssues() {
        return records.stream().flatMap(r -> r.issues().stream()).collect(Collectors.toList());
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
