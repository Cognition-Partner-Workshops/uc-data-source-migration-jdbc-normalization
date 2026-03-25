package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;

import java.util.List;

/**
 * Common interface for loan service operations.
 * Implemented by both the legacy {@link LoanService} and modern {@link ModernLoanService}.
 */
public interface LoanServiceInterface {

    List<LoanSummaryDto> getAllLoans();

    LoanSummaryDto getLoanById(String id);

    List<BorrowerDto> getAllBorrowers();

    BorrowerDto getBorrowerById(String id);

    List<PaymentDto> getPaymentsByLoan(String loanId);
}
