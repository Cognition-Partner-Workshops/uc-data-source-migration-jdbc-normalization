package com.modernbank.datasource.repository.staging;

import com.modernbank.datasource.model.staging.StagingLoan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StagingLoanRepository extends JpaRepository<StagingLoan, Long> {

    List<StagingLoan> findByBatchId(Long batchId);

    List<StagingLoan> findByProcessingStatus(String processingStatus);

    long countByBatchId(Long batchId);
}
