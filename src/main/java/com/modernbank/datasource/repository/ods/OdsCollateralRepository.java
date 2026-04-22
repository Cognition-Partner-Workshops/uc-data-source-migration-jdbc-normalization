package com.modernbank.datasource.repository.ods;

import com.modernbank.datasource.model.ods.OdsCollateralRecord;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OdsCollateralRepository extends JpaRepository<OdsCollateralRecord, Long> {

    Page<OdsCollateralRecord> findByProcessedFalse(Pageable pageable);

    List<OdsCollateralRecord> findByProcessedFalse();

    @Modifying
    @Query("UPDATE OdsCollateralRecord o SET o.processed = true WHERE o.id IN :ids")
    int markAsProcessed(@Param("ids") List<Long> ids);

    long countByProcessedFalse();
}
