package com.workshop.loanservice.repository;

import com.workshop.loanservice.entity.LegacyLoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LegacyLoanAccountRepository extends JpaRepository<LegacyLoanAccount, String> {

    List<LegacyLoanAccount> findByBorrowerId(String borrowerId);

    List<LegacyLoanAccount> findByStatusCode(String statusCode);

    List<LegacyLoanAccount> findByProductCode(String productCode);

    @Query(value = "SELECT * FROM CDW_LN_ACCT " +
            "ORDER BY CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2)) DESC",
            nativeQuery = true)
    List<LegacyLoanAccount> findAllOrderByCurrentBalanceDesc();

    @Query(value = "SELECT * FROM CDW_LN_ACCT " +
            "ORDER BY CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2)) ASC",
            nativeQuery = true)
    List<LegacyLoanAccount> findAllOrderByCurrentBalanceAsc();

    @Query(value = "SELECT * FROM CDW_LN_ACCT " +
            "WHERE CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2)) > :threshold " +
            "ORDER BY CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2)) ASC",
            nativeQuery = true)
    List<LegacyLoanAccount> findByCurrentBalanceGreaterThan(@Param("threshold") double threshold);

    @Query(value = "SELECT * FROM CDW_LN_ACCT " +
            "WHERE CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2)) < :threshold " +
            "ORDER BY CAST(REPLACE(LN_CURR_BAL, ',', '') AS DECIMAL(15,2)) DESC",
            nativeQuery = true)
    List<LegacyLoanAccount> findByCurrentBalanceLessThan(@Param("threshold") double threshold);

    @Query(value = "SELECT * FROM CDW_LN_ACCT " +
            "ORDER BY CAST(REPLACE(LN_ORIG_AMT, ',', '') AS DECIMAL(15,2)) DESC",
            nativeQuery = true)
    List<LegacyLoanAccount> findAllOrderByOriginalAmountDesc();

    @Query(value = "SELECT * FROM CDW_LN_ACCT " +
            "ORDER BY CAST(REPLACE(LN_INT_RT, ',', '') AS DECIMAL(8,4)) DESC",
            nativeQuery = true)
    List<LegacyLoanAccount> findAllOrderByInterestRateDesc();

    @Query(value = "SELECT * FROM CDW_LN_ACCT " +
            "ORDER BY CAST(LN_DLQ_DAYS AS INTEGER) DESC",
            nativeQuery = true)
    List<LegacyLoanAccount> findAllOrderByDelinquencyDaysDesc();
}
