package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyBorrower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegacyBorrowerRepository extends JpaRepository<LegacyBorrower, String> {

    List<LegacyBorrower> findByStatusCode(String statusCode);

    List<LegacyBorrower> findByLastNameIgnoreCase(String lastName);

    @Query(value = "SELECT * FROM CDW_BORR_MSTR " +
            "ORDER BY CAST(BORR_CRDT_SCR AS INTEGER) DESC",
            nativeQuery = true)
    List<LegacyBorrower> findAllOrderByCreditScoreDesc();

    @Query(value = "SELECT * FROM CDW_BORR_MSTR " +
            "WHERE CAST(REPLACE(BORR_ANN_INCM, ',', '') AS DECIMAL(15,2)) > :threshold " +
            "ORDER BY CAST(REPLACE(BORR_ANN_INCM, ',', '') AS DECIMAL(15,2)) DESC",
            nativeQuery = true)
    List<LegacyBorrower> findByAnnualIncomeGreaterThan(@Param("threshold") double threshold);
}
