package com.modernbank.datasource.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

public class BatchStepListener implements StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(BatchStepListener.class);

    private final String stepName;

    public BatchStepListener(String stepName) {
        this.stepName = stepName;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Starting batch step: {}", stepName);
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("Completed step {}: read={}, written={}, skipped={}, status={}",
                stepName,
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount(),
                stepExecution.getExitStatus().getExitCode());
        return stepExecution.getExitStatus();
    }
}
