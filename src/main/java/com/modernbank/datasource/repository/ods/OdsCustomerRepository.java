package com.modernbank.datasource.repository.ods;

import com.modernbank.datasource.model.ods.OdsCustomerRecord;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OdsCustomerRepository extends JpaRepository<OdsCustomerRecord, Long> {

    Page<OdsCustomerRecord> findByProcessedFalse(Pageable pageable);

    List<OdsCustomerRecord> findByProcessedFalse();

    @Modifying
    @Query("UPDATE OdsCustomerRecord o SET o.processed = true WHERE o.id IN :ids")
    int markAsProcessed(@Param("ids") List<Long> ids);

    long countByProcessedFalse();
}
