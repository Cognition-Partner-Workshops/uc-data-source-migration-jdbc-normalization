package com.workshop.loanservice.validation.report;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the full data quality report against the seeded legacy data.
 */
@SpringBootTest
class DataQualityServiceTest {

    @Autowired
    private DataQualityService dataQualityService;

    @Test
    void reportCoversAllFourLegacyTables() {
        DataQualityReport report = dataQualityService.generateReport();
        assertNotNull(report);
        assertEquals(4, report.tables().size());
    }

    @Test
    void seedDataCountsMatchExpectedRowCounts() {
        DataQualityReport report = dataQualityService.generateReport();
        assertEquals(5, tableReport(report, "CDW_BORR_MSTR").totalRecords());
        assertEquals(5, tableReport(report, "CDW_LN_PROD").totalRecords());
        assertEquals(5, tableReport(report, "CDW_LN_ACCT").totalRecords());
        assertEquals(10, tableReport(report, "CDW_PMT_HIST").totalRecords());
        assertEquals(25, report.totalRecords());
    }

    @Test
    void seedDataIsClean() {
        DataQualityReport report = dataQualityService.generateReport();
        assertEquals(0, report.errorCount(), "seed data should have no errors");
        assertEquals(25, report.cleanRecords());
        assertEquals(100.0, report.overallScore());
    }

    @Test
    void referentialIntegrityHoldsForSeededLoanAccounts() {
        DataQualityReport report = dataQualityService.generateReport();
        boolean anyFkIssue = tableReport(report, "CDW_LN_ACCT").allIssues().stream()
                .anyMatch(i -> i.ruleId().endsWith("_FK"));
        assertTrue(!anyFkIssue, "all loan accounts should reference existing borrowers and products");
    }

    private TableQualityReport tableReport(DataQualityReport report, String table) {
        return report.tables().stream()
                .filter(t -> t.table().equals(table))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing table report: " + table));
    }
}
