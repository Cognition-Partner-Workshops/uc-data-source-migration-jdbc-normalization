package com.workshop.loanservice.repository.modern;

import com.workshop.loanservice.entity.modern.Borrower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    Optional<Borrower> findByExternalId(String externalId);
}
