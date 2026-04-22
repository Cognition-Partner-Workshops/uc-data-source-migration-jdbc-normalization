package com.modernbank.datasource.repository.staging;

import com.modernbank.datasource.model.staging.StagingCustomer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StagingCustomerRepository extends JpaRepository<StagingCustomer, Long> {

    List<StagingCustomer> findByBatchId(Long batchId);

    List<StagingCustomer> findByProcessingStatus(String processingStatus);

    long countByBatchId(Long batchId);
}
