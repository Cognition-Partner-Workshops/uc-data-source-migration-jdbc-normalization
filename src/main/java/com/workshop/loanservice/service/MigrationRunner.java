package com.workshop.loanservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs the legacy-to-modern data migration automatically on application startup
 * when app.migration.run-on-startup=true.
 */
@Component
public class MigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final DataMigrationService migrationService;

    @Value("${app.migration.run-on-startup:false}")
    private boolean runOnStartup;

    public MigrationRunner(DataMigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!runOnStartup) {
            log.info("Data migration skipped (app.migration.run-on-startup=false)");
            return;
        }

        log.info("Starting legacy-to-modern data migration...");
        DataMigrationService.MigrationResult result = migrationService.migrateAll();

        if (result.hasErrors()) {
            log.warn("Migration completed with errors: {}", result);
        } else {
            log.info("Migration completed successfully: {}", result);
        }
    }
}
