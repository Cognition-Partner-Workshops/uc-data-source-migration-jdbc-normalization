package com.modernbank.datasource.repository.ods;

import com.modernbank.datasource.model.ods.OdsLoanRecord;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OdsLoanRepository extends JpaRepository<OdsLoanRecord, Long> {

    Page<OdsLoanRecord> findByProcessedFalse(Pageable pageable);

    List<OdsLoanRecord> findByProcessedFalse();

    @Modifying
    @Query("UPDATE OdsLoanRecord o SET o.processed = true WHERE o.id IN :ids")
    int markAsProcessed(@Param("ids") List<Long> ids);

    long countByProcessedFalse();
}
