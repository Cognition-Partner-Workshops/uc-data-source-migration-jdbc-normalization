package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.Borrower;
import com.workshop.loanservice.entity.LoanAccount;
import com.workshop.loanservice.entity.LoanProduct;
import com.workshop.loanservice.entity.Payment;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.repository.BorrowerRepository;
import com.workshop.loanservice.repository.LoanAccountRepository;
import com.workshop.loanservice.repository.LoanProductRepository;
import com.workshop.loanservice.repository.PaymentRepository;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service that migrates data from legacy tables to modern tables.
 * Handles all type conversions, FK resolution, and status code expansion.
 *
 * This service reads from the legacy CDW tables (all-VARCHAR, denormalized)
 * and writes to the modern normalized tables with proper types.
 */
@Service
public class DataMigrationService {

    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final LegacyBorrowerRepository legacyBorrowerRepo;
    private final LegacyLoanAccountRepository legacyLoanAccountRepo;
    private final LegacyLoanProductRepository legacyLoanProductRepo;
    private final LegacyPaymentRepository legacyPaymentRepo;

    private final BorrowerRepository borrowerRepo;
    private final LoanAccountRepository loanAccountRepo;
    private final LoanProductRepository loanProductRepo;
    private final PaymentRepository paymentRepo;

    public DataMigrationService(
            LegacyBorrowerRepository legacyBorrowerRepo,
            LegacyLoanAccountRepository legacyLoanAccountRepo,
            LegacyLoanProductRepository legacyLoanProductRepo,
            LegacyPaymentRepository legacyPaymentRepo,
            BorrowerRepository borrowerRepo,
            LoanAccountRepository loanAccountRepo,
            LoanProductRepository loanProductRepo,
            PaymentRepository paymentRepo) {
        this.legacyBorrowerRepo = legacyBorrowerRepo;
        this.legacyLoanAccountRepo = legacyLoanAccountRepo;
        this.legacyLoanProductRepo = legacyLoanProductRepo;
        this.legacyPaymentRepo = legacyPaymentRepo;
        this.borrowerRepo = borrowerRepo;
        this.loanAccountRepo = loanAccountRepo;
        this.loanProductRepo = loanProductRepo;
        this.paymentRepo = paymentRepo;
    }

    @Transactional
    public MigrationResult migrateAll() {
        MigrationResult result = new MigrationResult();

        result.borrowersMigrated = migrateBorrowers();
        result.productsMigrated = migrateLoanProducts();
        result.loanAccountsMigrated = migrateLoanAccounts();
        result.paymentsMigrated = migratePayments();

        return result;
    }

    private int migrateBorrowers() {
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepo.findAll();
        int count = 0;

        for (LegacyBorrower legacy : legacyBorrowers) {
            Borrower modern = new Borrower();
            modern.setExternalId(legacy.getBorrowerId());
            modern.setFirstName(legacy.getFirstName());
            modern.setLastName(legacy.getLastName());
            modern.setMiddleInitial(legacy.getMiddleInitial());
            modern.setSsnHash(legacy.getSsnEncrypted());
            modern.setDateOfBirth(parseLegacyDate(legacy.getDateOfBirth()));
            modern.setAddressLine1(legacy.getAddressLine1());
            modern.setAddressLine2(legacy.getAddressLine2());
            modern.setCity(legacy.getCity());
            modern.setState(legacy.getStateCode());
            modern.setZipCode(legacy.getZipCode());
            modern.setPhone(legacy.getPhoneNumber());
            modern.setEmail(legacy.getEmail());
            modern.setCreditScore(parseInteger(legacy.getCreditScore()));
            modern.setEmploymentStatus(legacy.getEmploymentStatus());
            modern.setAnnualIncome(parseLegacyAmount(legacy.getAnnualIncome()));
            modern.setStatus(expandBorrowerStatus(legacy.getStatusCode()));
            modern.setCreatedAt(parseLegacyDateTime(legacy.getCreatedDate()));
            modern.setUpdatedAt(parseLegacyDateTime(legacy.getUpdatedDate()));

            borrowerRepo.save(modern);
            count++;
        }
        return count;
    }

    private int migrateLoanProducts() {
        List<LegacyLoanProduct> legacyProducts = legacyLoanProductRepo.findAll();
        int count = 0;

        for (LegacyLoanProduct legacy : legacyProducts) {
            LoanProduct modern = new LoanProduct();
            modern.setCode(legacy.getProductCode());
            modern.setName(legacy.getDescription());
            modern.setType(legacy.getTypeCode());
            modern.setTermMonths(parseInteger(legacy.getTermMonths()));
            modern.setRateType(legacy.getRateType());
            modern.setMinAmount(parseLegacyAmount(legacy.getMinAmount()));
            modern.setMaxAmount(parseLegacyAmount(legacy.getMaxAmount()));
            modern.setIsActive("ACT".equals(legacy.getStatusCode()));
            modern.setEffectiveDate(parseLegacyDate(legacy.getEffectiveDate()));
            modern.setExpirationDate(parseLegacyDate(legacy.getExpirationDate()));

            loanProductRepo.save(modern);
            count++;
        }
        return count;
    }

    private int migrateLoanAccounts() {
        List<LegacyLoanAccount> legacyAccounts = legacyLoanAccountRepo.findAll();

        // Build lookup maps for FK resolution
        Map<String, Borrower> borrowerMap = new HashMap<>();
        borrowerRepo.findAll().forEach(b -> borrowerMap.put(b.getExternalId(), b));

        Map<String, LoanProduct> productMap = new HashMap<>();
        loanProductRepo.findAll().forEach(p -> productMap.put(p.getCode(), p));

        int count = 0;
        for (LegacyLoanAccount legacy : legacyAccounts) {
            Borrower borrower = borrowerMap.get(legacy.getBorrowerId());
            LoanProduct product = productMap.get(legacy.getProductCode());

            if (borrower == null || product == null) {
                continue; // Skip records with missing FK references
            }

            LoanAccount modern = new LoanAccount();
            modern.setAccountNumber(legacy.getLoanAccountNumber());
            modern.setBorrower(borrower);
            modern.setProduct(product);
            modern.setOriginalAmount(parseLegacyAmount(legacy.getOriginalAmount()));
            modern.setCurrentBalance(parseLegacyAmount(legacy.getCurrentBalance()));
            modern.setInterestRate(parseLegacyDecimal(legacy.getInterestRate()));
            modern.setTermMonths(parseInteger(legacy.getTermMonths()));
            modern.setMonthlyPayment(parseLegacyAmount(legacy.getMonthlyPayment()));
            modern.setOriginationDate(parseLegacyDate(legacy.getOriginationDate()));
            modern.setMaturityDate(parseLegacyDate(legacy.getMaturityDate()));
            modern.setFirstPaymentDate(parseLegacyDate(legacy.getFirstPaymentDate()));
            modern.setNextPaymentDate(parseLegacyDate(legacy.getNextPaymentDate()));
            modern.setStatus(expandLoanStatus(legacy.getStatusCode()));
            modern.setDelinquencyDays(parseInteger(legacy.getDelinquencyDays()));
            modern.setEscrowBalance(parseLegacyAmount(legacy.getEscrowBalance()));
            modern.setLtvPercent(parseLegacyDecimal(legacy.getLtvPercent()));
            modern.setPropertyAddress(legacy.getPropertyAddress());
            modern.setPropertyCity(legacy.getPropertyCity());
            modern.setPropertyState(legacy.getPropertyState());
            modern.setPropertyZip(legacy.getPropertyZip());
            modern.setPropertyType(expandPropertyType(legacy.getPropertyType()));
            modern.setAppraisedValue(parseLegacyAmount(legacy.getAppraisedValue()));
            modern.setCreatedAt(parseLegacyDateTime(legacy.getCreatedDate()));
            modern.setUpdatedAt(parseLegacyDateTime(legacy.getUpdatedDate()));

            loanAccountRepo.save(modern);
            count++;
        }
        return count;
    }

    private int migratePayments() {
        List<LegacyPayment> legacyPayments = legacyPaymentRepo.findAll();

        // Build lookup map for FK resolution
        Map<String, LoanAccount> accountMap = new HashMap<>();
        loanAccountRepo.findAll().forEach(a -> accountMap.put(a.getAccountNumber(), a));

        int count = 0;
        for (LegacyPayment legacy : legacyPayments) {
            LoanAccount loanAccount = accountMap.get(legacy.getLoanAccountNumber());

            if (loanAccount == null) {
                continue; // Skip records with missing FK references
            }

            Payment modern = new Payment();
            modern.setLoanAccount(loanAccount);
            modern.setPaymentDate(parseLegacyDate(legacy.getPaymentDate()));
            modern.setTotalAmount(parseLegacyAmount(legacy.getTotalAmount()));
            modern.setPrincipalAmount(parseLegacyAmount(legacy.getPrincipalAmount()));
            modern.setInterestAmount(parseLegacyAmount(legacy.getInterestAmount()));
            modern.setEscrowAmount(parseLegacyAmount(legacy.getEscrowAmount()));
            modern.setLateFee(parseLegacyAmount(legacy.getLateFee()));
            modern.setType(expandPaymentType(legacy.getTypeCode()));
            modern.setStatus(expandPaymentStatus(legacy.getStatusCode()));
            modern.setReceivedDate(parseLegacyDate(legacy.getReceivedDate()));
            modern.setProcessedDate(parseLegacyDate(legacy.getProcessedDate()));
            modern.setCreatedAt(parseLegacyDateTime(legacy.getCreatedDate()));
            modern.setUpdatedAt(parseLegacyDateTime(legacy.getUpdatedDate()));

            paymentRepo.save(modern);
            count++;
        }
        return count;
    }

    // =========================================================================
    // TYPE CONVERSION HELPERS
    // =========================================================================

    private BigDecimal parseLegacyAmount(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(amount.replace(",", ""));
    }

    private BigDecimal parseLegacyDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim());
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return 0;
        return Integer.parseInt(value.trim());
    }

    private LocalDate parseLegacyDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr, LEGACY_DATE_FORMAT);
    }

    private LocalDateTime parseLegacyDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return LocalDateTime.now();
        return LocalDate.parse(dateStr, LEGACY_DATE_FORMAT).atStartOfDay();
    }

    // =========================================================================
    // STATUS CODE EXPANSION
    // =========================================================================

    private String expandBorrowerStatus(String code) {
        if (code == null) return "ACTIVE";
        return switch (code) {
            case "ACT" -> "ACTIVE";
            case "INA" -> "INACTIVE";
            default -> code;
        };
    }

    private String expandLoanStatus(String code) {
        if (code == null) return "ACTIVE";
        return switch (code) {
            case "ACT" -> "ACTIVE";
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

    /**
     * Migration result summary.
     */
    public static class MigrationResult {
        public int borrowersMigrated;
        public int productsMigrated;
        public int loanAccountsMigrated;
        public int paymentsMigrated;

        @Override
        public String toString() {
            return String.format(
                "Migration complete: %d borrowers, %d products, %d loan accounts, %d payments",
                borrowersMigrated, productsMigrated, loanAccountsMigrated, paymentsMigrated);
        }
    }
}
