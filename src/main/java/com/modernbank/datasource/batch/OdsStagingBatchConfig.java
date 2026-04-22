package com.modernbank.datasource.batch;

import com.modernbank.datasource.model.ods.OdsCollateralRecord;
import com.modernbank.datasource.model.ods.OdsCustomerRecord;
import com.modernbank.datasource.model.ods.OdsLoanRecord;
import com.modernbank.datasource.model.staging.StagingCollateral;
import com.modernbank.datasource.model.staging.StagingCustomer;
import com.modernbank.datasource.model.staging.StagingLoan;
import com.modernbank.datasource.service.OdsDataExtractorService;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch configuration for extracting ODS operational data
 * and writing it to staging tables in batches.
 */
@Configuration
public class OdsStagingBatchConfig {

    private static final Logger log = LoggerFactory.getLogger(OdsStagingBatchConfig.class);

    @Value("${batch.chunk-size:500}")
    private int chunkSize;

    @Value("${batch.page-size:500}")
    private int pageSize;

    private final EntityManagerFactory entityManagerFactory;
    private final OdsDataExtractorService extractorService;

    public OdsStagingBatchConfig(EntityManagerFactory entityManagerFactory,
                                  OdsDataExtractorService extractorService) {
        this.entityManagerFactory = entityManagerFactory;
        this.extractorService = extractorService;
    }

    // ---- Loan Step ----

    @Bean
    public JpaPagingItemReader<OdsLoanRecord> odsLoanReader() {
        return new JpaPagingItemReaderBuilder<OdsLoanRecord>()
                .name("odsLoanReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT o FROM OdsLoanRecord o WHERE o.processed = false ORDER BY o.id")
                .pageSize(pageSize)
                .build();
    }

    @Bean
    public ItemProcessor<OdsLoanRecord, StagingLoan> loanProcessor() {
        return odsRecord -> {
            long batchId = System.currentTimeMillis();
            return extractorService.transformLoan(odsRecord, batchId);
        };
    }

    @Bean
    public JpaItemWriter<StagingLoan> stagingLoanWriter() {
        return new JpaItemWriterBuilder<StagingLoan>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step loanStagingStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager) {
        return new StepBuilder("loanStagingStep", jobRepository)
                .<OdsLoanRecord, StagingLoan>chunk(chunkSize, transactionManager)
                .reader(odsLoanReader())
                .processor(loanProcessor())
                .writer(stagingLoanWriter())
                .listener(new BatchStepListener("loanStagingStep"))
                .build();
    }

    // ---- Customer Step ----

    @Bean
    public JpaPagingItemReader<OdsCustomerRecord> odsCustomerReader() {
        return new JpaPagingItemReaderBuilder<OdsCustomerRecord>()
                .name("odsCustomerReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT o FROM OdsCustomerRecord o WHERE o.processed = false ORDER BY o.id")
                .pageSize(pageSize)
                .build();
    }

    @Bean
    public ItemProcessor<OdsCustomerRecord, StagingCustomer> customerProcessor() {
        return odsRecord -> {
            long batchId = System.currentTimeMillis();
            return extractorService.transformCustomer(odsRecord, batchId);
        };
    }

    @Bean
    public JpaItemWriter<StagingCustomer> stagingCustomerWriter() {
        return new JpaItemWriterBuilder<StagingCustomer>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step customerStagingStep(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager) {
        return new StepBuilder("customerStagingStep", jobRepository)
                .<OdsCustomerRecord, StagingCustomer>chunk(chunkSize, transactionManager)
                .reader(odsCustomerReader())
                .processor(customerProcessor())
                .writer(stagingCustomerWriter())
                .listener(new BatchStepListener("customerStagingStep"))
                .build();
    }

    // ---- Collateral Step ----

    @Bean
    public JpaPagingItemReader<OdsCollateralRecord> odsCollateralReader() {
        return new JpaPagingItemReaderBuilder<OdsCollateralRecord>()
                .name("odsCollateralReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT o FROM OdsCollateralRecord o WHERE o.processed = false ORDER BY o.id")
                .pageSize(pageSize)
                .build();
    }

    @Bean
    public ItemProcessor<OdsCollateralRecord, StagingCollateral> collateralProcessor() {
        return odsRecord -> {
            long batchId = System.currentTimeMillis();
            return extractorService.transformCollateral(odsRecord, batchId);
        };
    }

    @Bean
    public JpaItemWriter<StagingCollateral> stagingCollateralWriter() {
        return new JpaItemWriterBuilder<StagingCollateral>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }

    @Bean
    public Step collateralStagingStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager) {
        return new StepBuilder("collateralStagingStep", jobRepository)
                .<OdsCollateralRecord, StagingCollateral>chunk(chunkSize, transactionManager)
                .reader(odsCollateralReader())
                .processor(collateralProcessor())
                .writer(stagingCollateralWriter())
                .listener(new BatchStepListener("collateralStagingStep"))
                .build();
    }

    // ---- Job ----

    @Bean
    public Job odsStagingJob(JobRepository jobRepository,
                              Step loanStagingStep,
                              Step customerStagingStep,
                              Step collateralStagingStep) {
        return new JobBuilder("odsStagingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(loanStagingStep)
                .next(customerStagingStep)
                .next(collateralStagingStep)
                .build();
    }
}
