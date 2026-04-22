package com.modernbank.datasource.controller;

import com.modernbank.datasource.service.StagingToMvService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch")
public class BatchJobController {

    private static final Logger log = LoggerFactory.getLogger(BatchJobController.class);

    private final JobLauncher jobLauncher;
    private final Job odsStagingJob;
    private final StagingToMvService stagingToMvService;

    public BatchJobController(JobLauncher jobLauncher,
                               Job odsStagingJob,
                               StagingToMvService stagingToMvService) {
        this.jobLauncher = jobLauncher;
        this.odsStagingJob = odsStagingJob;
        this.stagingToMvService = stagingToMvService;
    }

    @PostMapping("/ods-to-staging")
    public ResponseEntity<Map<String, Object>> runOdsToStaging() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(odsStagingJob, params);

            return ResponseEntity.ok(Map.of(
                    "status", execution.getExitStatus().getExitCode(),
                    "jobId", execution.getJobId(),
                    "message", "ODS to Staging batch job completed"
            ));
        } catch (Exception e) {
            log.error("Failed to run ODS to staging batch job", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/staging-to-mv")
    public ResponseEntity<Map<String, Object>> runStagingToMv(
            @RequestParam(required = false) Long batchId) {
        try {
            stagingToMvService.loadAllMaterializedViews(batchId);
            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETED",
                    "message", "Staging to MV load completed",
                    "batchId", batchId != null ? batchId : "ALL"
            ));
        } catch (Exception e) {
            log.error("Failed to run staging to MV load", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/full-pipeline")
    public ResponseEntity<Map<String, Object>> runFullPipeline() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(odsStagingJob, params);

            if ("COMPLETED".equals(execution.getExitStatus().getExitCode())) {
                stagingToMvService.loadAllMaterializedViews(null);
                return ResponseEntity.ok(Map.of(
                        "status", "COMPLETED",
                        "jobId", execution.getJobId(),
                        "message", "Full pipeline (ODS -> Staging -> MV) completed"
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "status", execution.getExitStatus().getExitCode(),
                        "jobId", execution.getJobId(),
                        "message", "ODS to Staging step did not complete successfully"
                ));
            }
        } catch (Exception e) {
            log.error("Failed to run full pipeline", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "FAILED",
                    "error", e.getMessage()
            ));
        }
    }
}
