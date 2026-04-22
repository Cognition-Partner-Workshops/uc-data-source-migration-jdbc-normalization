package com.modernbank.datasource.repository.staging;

import com.modernbank.datasource.model.staging.StagingCollateral;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StagingCollateralRepository extends JpaRepository<StagingCollateral, Long> {

    List<StagingCollateral> findByBatchId(Long batchId);

    List<StagingCollateral> findByProcessingStatus(String processingStatus);

    long countByBatchId(Long batchId);
}
