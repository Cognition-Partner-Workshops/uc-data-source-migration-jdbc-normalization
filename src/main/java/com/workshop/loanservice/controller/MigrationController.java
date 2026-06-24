package com.workshop.loanservice.controller;

import com.workshop.loanservice.service.DataMigrationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class MigrationController {

    private final DataMigrationService dataMigrationService;

    public MigrationController(DataMigrationService dataMigrationService) {
        this.dataMigrationService = dataMigrationService;
    }

    @PostMapping("/migrate")
    public Map<String, Integer> migrate() {
        return dataMigrationService.migrate();
    }
}
