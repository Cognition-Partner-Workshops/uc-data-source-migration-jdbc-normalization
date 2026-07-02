package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegacyPaymentRepository extends JpaRepository<LegacyPayment, String> {

    List<LegacyPayment> findByLoanAccountNumber(String loanAccountNumber);

    @Query(value = "SELECT * FROM CDW_PMT_HIST WHERE LN_ACCT_NBR = :loanAccountNumber " +
            "ORDER BY PARSEDATETIME(PMT_DT, 'MM/dd/yyyy') DESC",
            nativeQuery = true)
    List<LegacyPayment> findByLoanAccountNumberOrderByPaymentDateDesc(
            @Param("loanAccountNumber") String loanAccountNumber);

    @Query(value = "SELECT * FROM CDW_PMT_HIST WHERE LN_ACCT_NBR = :loanAccountNumber " +
            "ORDER BY CAST(REPLACE(PMT_AMT, ',', '') AS DECIMAL(15,2)) DESC",
            nativeQuery = true)
    List<LegacyPayment> findByLoanAccountNumberOrderByTotalAmountDesc(
            @Param("loanAccountNumber") String loanAccountNumber);
}
