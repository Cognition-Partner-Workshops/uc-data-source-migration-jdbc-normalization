package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Modern service layer that reads from the normalized SQL Server tables.
 * Activated with the "modern" or "sqlserver" Spring profile.
 *
 * Key differences from the legacy LoanService:
 * - No string-to-type parsing needed (modern entities use proper Java types)
 * - Uses JPA relationships for joins (borrower, product via FK)
 * - Simplified DTO mapping (no status code expansion needed — already expanded)
 */
@Service
@Profile({"modern", "sqlserver"})
public class ModernLoanService implements LoanServiceInterface {

    private final BorrowerRepository borrowerRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    public ModernLoanService(BorrowerRepository borrowerRepository,
                             LoanAccountRepository loanAccountRepository,
                             PaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<LoanSummaryDto> getAllLoans() {
        return loanAccountRepository.findAll().stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
    }

    public LoanSummaryDto getLoanById(String accountNumber) {
        LoanAccount acct = loanAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Loan not found: " + accountNumber));
        return toLoanSummary(acct);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    public BorrowerDto getBorrowerById(String externalId) {
        Borrower borrower = borrowerRepository.findByExternalId(externalId)
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + externalId));
        BorrowerDto dto = toBorrowerDto(borrower);

        List<LoanSummaryDto> loans = loanAccountRepository.findByBorrowerExternalId(externalId)
                .stream()
                .map(this::toLoanSummary)
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    public List<PaymentDto> getPaymentsByLoan(String accountNumber) {
        return paymentRepository.findByLoanAccountAccountNumberOrderByPaymentDateDesc(accountNumber)
                .stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // DTO MAPPING — No legacy string parsing needed!
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LoanAccount acct) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getAccountNumber());
        dto.setBorrowerName(acct.getBorrower().getFirstName() + " " + acct.getBorrower().getLastName());
        dto.setProductDescription(acct.getProduct().getName());
        dto.setOriginalAmount(acct.getOriginalAmount());
        dto.setCurrentBalance(acct.getCurrentBalance());
        dto.setInterestRate(acct.getInterestRate());
        dto.setMonthlyPayment(acct.getMonthlyPayment());
        dto.setStatus(acct.getStatus());
        dto.setOriginationDate(acct.getOriginationDate() != null
                ? acct.getOriginationDate().toString() : null);
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(acct.getPropertyType());
        return dto;
    }

    private BorrowerDto toBorrowerDto(Borrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getExternalId());
        String middle = borrower.getMiddleInitial() != null
                ? " " + borrower.getMiddleInitial() + "." : "";
        dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
        dto.setEmail(borrower.getEmail());
        dto.setPhone(borrower.getPhone());
        dto.setCity(borrower.getCity());
        dto.setState(borrower.getState());
        dto.setCreditScore(borrower.getCreditScore());
        dto.setEmploymentStatus(borrower.getEmploymentStatus());
        return dto;
    }

    private PaymentDto toPaymentDto(Payment pmt) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(String.valueOf(pmt.getId()));
        dto.setLoanAccountNumber(pmt.getLoanAccount().getAccountNumber());
        dto.setPaymentDate(pmt.getPaymentDate() != null
                ? pmt.getPaymentDate().toString() : null);
        dto.setTotalAmount(pmt.getTotalAmount());
        dto.setPrincipalAmount(pmt.getPrincipalAmount());
        dto.setInterestAmount(pmt.getInterestAmount());
        dto.setEscrowAmount(pmt.getEscrowAmount());
        dto.setLateFee(pmt.getLateFee());
        dto.setType(pmt.getType());
        dto.setStatus(pmt.getStatus());
        return dto;
    }
}
