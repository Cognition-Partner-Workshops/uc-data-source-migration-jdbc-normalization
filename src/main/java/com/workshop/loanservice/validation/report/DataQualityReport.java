package com.workshop.loanservice.validation.report;

import java.time.Instant;
import java.util.List;

/**
 * Top-level data quality report spanning all legacy tables.
 */
public record DataQualityReport(
        Instant generatedAt,
        int totalRecords,
        int cleanRecords,
        double overallScore,
        long errorCount,
        long warningCount,
        long infoCount,
        List<TableQualityReport> tables) {

    /** Aggregate a set of per-table reports into an overall report. */
    public static DataQualityReport of(List<TableQualityReport> tables) {
        int total = tables.stream().mapToInt(TableQualityReport::totalRecords).sum();
        int clean = tables.stream().mapToInt(TableQualityReport::cleanRecords).sum();
        long errors = tables.stream().mapToLong(TableQualityReport::errorCount).sum();
        long warnings = tables.stream().mapToLong(TableQualityReport::warningCount).sum();
        long infos = tables.stream().mapToLong(TableQualityReport::infoCount).sum();

        // Overall score is the record-weighted average of table scores.
        double weighted = total == 0 ? 100.0
                : tables.stream()
                        .mapToDouble(t -> t.averageScore() * t.totalRecords())
                        .sum() / total;

        return new DataQualityReport(
                Instant.now(),
                total,
                clean,
                Math.round(weighted * 100.0) / 100.0,
                errors,
                warnings,
                infos,
                tables);
    }
}
