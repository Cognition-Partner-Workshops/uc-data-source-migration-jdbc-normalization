package com.workshop.loanservice.controller;

import com.workshop.loanservice.validation.report.DataQualityReport;
import com.workshop.loanservice.validation.report.DataQualityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the legacy data quality report.
 */
@RestController
@RequestMapping("/api/data-quality")
public class DataQualityController {

    private final DataQualityService dataQualityService;

    public DataQualityController(DataQualityService dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @GetMapping
    public DataQualityReport getReport() {
        return dataQualityService.generateReport();
    }
}
