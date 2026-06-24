package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataMigrationService {

    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final LegacyBorrowerRepository legacyBorrowerRepository;
    private final LegacyLoanProductRepository legacyLoanProductRepository;
    private final LegacyLoanAccountRepository legacyLoanAccountRepository;
    private final LegacyPaymentRepository legacyPaymentRepository;

    private final BorrowerRepository borrowerRepository;
    private final LoanProductRepository loanProductRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final PaymentRepository paymentRepository;

    public DataMigrationService(LegacyBorrowerRepository legacyBorrowerRepository,
                                LegacyLoanProductRepository legacyLoanProductRepository,
                                LegacyLoanAccountRepository legacyLoanAccountRepository,
                                LegacyPaymentRepository legacyPaymentRepository,
                                BorrowerRepository borrowerRepository,
                                LoanProductRepository loanProductRepository,
                                LoanAccountRepository loanAccountRepository,
                                PaymentRepository paymentRepository) {
        this.legacyBorrowerRepository = legacyBorrowerRepository;
        this.legacyLoanProductRepository = legacyLoanProductRepository;
        this.legacyLoanAccountRepository = legacyLoanAccountRepository;
        this.legacyPaymentRepository = legacyPaymentRepository;
        this.borrowerRepository = borrowerRepository;
        this.loanProductRepository = loanProductRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Map<String, Integer> migrate() {
        Map<String, Integer> summary = new HashMap<>();

        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepository.findAll();
        for (LegacyBorrower lb : legacyBorrowers) {
            Borrower borrower = migrateBorrower(lb);
            borrowerRepository.save(borrower);
        }
        summary.put("borrowers", legacyBorrowers.size());

        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepository.findAll();
        for (LegacyLoanProduct lp : legacyProducts) {
            LoanProduct product = migrateLoanProduct(lp);
            loanProductRepository.save(product);
        }
        summary.put("loan_products", legacyProducts.size());

        List<LegacyLoanAccount> legacyAccounts = legacyLoanAccountRepository.findAll();
        for (LegacyLoanAccount la : legacyAccounts) {
            LoanAccount account = migrateLoanAccount(la);
            loanAccountRepository.save(account);
        }
        summary.put("loan_accounts", legacyAccounts.size());

        List<LegacyPayment> legacyPayments = legacyPaymentRepository.findAll();
        for (LegacyPayment lpm : legacyPayments) {
            Payment payment = migratePayment(lpm);
            paymentRepository.save(payment);
        }
        summary.put("payments", legacyPayments.size());

        return summary;
    }

    private Borrower migrateBorrower(LegacyBorrower lb) {
        Borrower b = new Borrower();
        b.setExternalId(lb.getBorrowerId());
        b.setFirstName(lb.getFirstName());
        b.setLastName(lb.getLastName());
        b.setMiddleInitial(lb.getMiddleInitial());
        b.setSsnHash(lb.getSsnEncrypted());
        b.setDateOfBirth(parseLegacyDate(lb.getDateOfBirth()));
        b.setAddressLine1(lb.getAddressLine1());
        b.setAddressLine2(lb.getAddressLine2());
        b.setCity(lb.getCity());
        b.setState(lb.getStateCode());
        b.setZipCode(lb.getZipCode());
        b.setPhone(lb.getPhoneNumber());
        b.setEmail(lb.getEmail());
        b.setCreditScore(parseInteger(lb.getCreditScore()));
        b.setEmploymentStatus(lb.getEmploymentStatus());
        b.setAnnualIncome(parseAmount(lb.getAnnualIncome()));
        b.setStatus(expandBorrowerStatus(lb.getStatusCode()));
        b.setCreatedAt(parseLegacyTimestamp(lb.getCreatedDate()));
        b.setUpdatedAt(parseLegacyTimestamp(lb.getUpdatedDate()));
        return b;
    }

    private LoanProduct migrateLoanProduct(LegacyLoanProduct lp) {
        LoanProduct p = new LoanProduct();
        p.setCode(lp.getProductCode());
        p.setName(lp.getDescription());
        p.setType(lp.getTypeCode());
        p.setTermMonths(parseInteger(lp.getTermMonths()));
        p.setRateType(lp.getRateType());
        p.setMinAmount(parseAmount(lp.getMinAmount()));
        p.setMaxAmount(parseAmount(lp.getMaxAmount()));
        p.setIsActive("ACT".equals(lp.getStatusCode()));
        p.setEffectiveDate(parseLegacyDate(lp.getEffectiveDate()));
        p.setExpirationDate(parseLegacyDate(lp.getExpirationDate()));
        return p;
    }

    private LoanAccount migrateLoanAccount(LegacyLoanAccount la) {
        LoanAccount a = new LoanAccount();
        a.setAccountNumber(la.getLoanAccountNumber());

        Borrower borrower = borrowerRepository.findByExternalId(la.getBorrowerId())
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + la.getBorrowerId()));
        a.setBorrower(borrower);

        LoanProduct product = loanProductRepository.findByCode(la.getProductCode())
                .orElseThrow(() -> new RuntimeException("Product not found: " + la.getProductCode()));
        a.setProduct(product);

        a.setOriginalAmount(parseAmount(la.getOriginalAmount()));
        a.setCurrentBalance(parseAmount(la.getCurrentBalance()));
        a.setInterestRate(parseDecimal(la.getInterestRate()));
        a.setTermMonths(parseInteger(la.getTermMonths()));
        a.setMonthlyPayment(parseAmount(la.getMonthlyPayment()));
        a.setOriginationDate(parseLegacyDate(la.getOriginationDate()));
        a.setMaturityDate(parseLegacyDate(la.getMaturityDate()));
        a.setFirstPaymentDate(parseLegacyDate(la.getFirstPaymentDate()));
        a.setNextPaymentDate(parseLegacyDate(la.getNextPaymentDate()));
        a.setStatus(expandAccountStatus(la.getStatusCode()));
        a.setDelinquencyDays(parseInteger(la.getDelinquencyDays()));
        a.setEscrowBalance(parseAmount(la.getEscrowBalance()));
        a.setLtvPercent(parseDecimal(la.getLtvPercent()));
        a.setPropertyAddress(la.getPropertyAddress());
        a.setPropertyCity(la.getPropertyCity());
        a.setPropertyState(la.getPropertyState());
        a.setPropertyZip(la.getPropertyZip());
        a.setPropertyType(expandPropertyType(la.getPropertyType()));
        a.setAppraisedValue(parseAmount(la.getAppraisedValue()));
        a.setCreatedAt(parseLegacyTimestamp(la.getCreatedDate()));
        a.setUpdatedAt(parseLegacyTimestamp(la.getUpdatedDate()));
        return a;
    }

    private Payment migratePayment(LegacyPayment lpm) {
        Payment p = new Payment();

        LoanAccount account = loanAccountRepository.findByAccountNumber(lpm.getLoanAccountNumber())
                .orElseThrow(() -> new RuntimeException("Loan account not found: " + lpm.getLoanAccountNumber()));
        p.setLoanAccount(account);

        p.setPaymentDate(parseLegacyDate(lpm.getPaymentDate()));
        p.setTotalAmount(parseAmount(lpm.getTotalAmount()));
        p.setPrincipalAmount(parseAmount(lpm.getPrincipalAmount()));
        p.setInterestAmount(parseAmount(lpm.getInterestAmount()));
        p.setEscrowAmount(parseAmount(lpm.getEscrowAmount()));
        p.setLateFee(parseAmount(lpm.getLateFee()));
        p.setType(expandPaymentType(lpm.getTypeCode()));
        p.setStatus(expandPaymentStatus(lpm.getStatusCode()));
        p.setReceivedDate(parseLegacyDate(lpm.getReceivedDate()));
        p.setProcessedDate(parseLegacyDate(lpm.getProcessedDate()));
        p.setCreatedAt(parseLegacyTimestamp(lpm.getCreatedDate()));
        p.setUpdatedAt(parseLegacyTimestamp(lpm.getUpdatedDate()));
        return p;
    }

    private LocalDate parseLegacyDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT);
    }

    private LocalDateTime parseLegacyTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        LocalDate date = LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT);
        return date.atStartOfDay();
    }

    private BigDecimal parseAmount(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(amount.replace(",", "").trim());
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim());
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return 0;
        return Integer.parseInt(value.trim());
    }

    private String expandBorrowerStatus(String code) {
        if (code == null) return "ACTIVE";
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> code;
        };
    }

    private String expandAccountStatus(String code) {
        if (code == null) return "ACTIVE";
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            case "CLO" -> "CLOSED";
            case "DFT" -> "DEFAULT";
            case "FRB" -> "FORBEARANCE";
            default -> code;
        };
    }

    private String expandPropertyType(String code) {
        if (code == null) return null;
        return switch (code) {
            case "SFR" -> "Single Family";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }

    private String expandPaymentType(String code) {
        if (code == null) return "REGULAR";
        return switch (code) {
            case "REG" -> "REGULAR";
            case "EXT" -> "EXTRA";
            case "PRT" -> "PARTIAL";
            case "PRE" -> "PREPAYMENT";
            default -> code;
        };
    }

    private String expandPaymentStatus(String code) {
        if (code == null) return "PENDING";
        return switch (code) {
            case "PST" -> "POSTED";
            case "REV" -> "REVERSED";
            case "NSF" -> "NSF";
            case "PND" -> "PENDING";
            default -> code;
        };
    }
}
