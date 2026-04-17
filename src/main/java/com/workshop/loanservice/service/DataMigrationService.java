package com.workshop.loanservice.service;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.entity.modern.Borrower;
import com.workshop.loanservice.entity.modern.LoanAccount;
import com.workshop.loanservice.entity.modern.LoanProduct;
import com.workshop.loanservice.entity.modern.Payment;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import com.workshop.loanservice.repository.modern.BorrowerRepository;
import com.workshop.loanservice.repository.modern.LoanAccountRepository;
import com.workshop.loanservice.repository.modern.LoanProductRepository;
import com.workshop.loanservice.repository.modern.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Migrates data from legacy CDW tables to the modern normalized schema.
 *
 * Transformation rules follow data/mappings/column_mappings.md:
 * - Dates: MM/DD/YYYY strings -> LocalDate / LocalDateTime
 * - Amounts: comma-separated strings -> BigDecimal
 * - Status codes: abbreviated codes -> expanded values
 * - Foreign keys: string IDs resolved to modern BIGINT PKs
 * - Property types: abbreviated codes -> full descriptions
 */
@Service
public class DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);
    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private final LegacyBorrowerRepository legacyBorrowerRepo;
    private final LegacyLoanProductRepository legacyProductRepo;
    private final LegacyLoanAccountRepository legacyAccountRepo;
    private final LegacyPaymentRepository legacyPaymentRepo;

    private final BorrowerRepository borrowerRepo;
    private final LoanProductRepository productRepo;
    private final LoanAccountRepository accountRepo;
    private final PaymentRepository paymentRepo;

    public DataMigrationService(LegacyBorrowerRepository legacyBorrowerRepo,
                                LegacyLoanProductRepository legacyProductRepo,
                                LegacyLoanAccountRepository legacyAccountRepo,
                                LegacyPaymentRepository legacyPaymentRepo,
                                BorrowerRepository borrowerRepo,
                                LoanProductRepository productRepo,
                                LoanAccountRepository accountRepo,
                                PaymentRepository paymentRepo) {
        this.legacyBorrowerRepo = legacyBorrowerRepo;
        this.legacyProductRepo = legacyProductRepo;
        this.legacyAccountRepo = legacyAccountRepo;
        this.legacyPaymentRepo = legacyPaymentRepo;
        this.borrowerRepo = borrowerRepo;
        this.productRepo = productRepo;
        this.accountRepo = accountRepo;
        this.paymentRepo = paymentRepo;
    }

    @Transactional
    public MigrationResult migrateAll() {
        MigrationResult result = new MigrationResult();

        if (borrowerRepo.count() > 0) {
            log.info("Modern tables already contain data, skipping migration");
            result.borrowersMigrated = (int) borrowerRepo.count();
            result.productsMigrated = (int) productRepo.count();
            result.accountsMigrated = (int) accountRepo.count();
            result.paymentsMigrated = (int) paymentRepo.count();
            return result;
        }

        Map<String, Borrower> borrowerMap = migrateBorrowers(result);
        Map<String, LoanProduct> productMap = migrateLoanProducts(result);
        Map<String, LoanAccount> accountMap = migrateLoanAccounts(borrowerMap, productMap, result);
        migratePayments(accountMap, result);

        log.info("Migration complete: {}", result);
        return result;
    }

    private Map<String, Borrower> migrateBorrowers(MigrationResult result) {
        List<LegacyBorrower> legacyBorrowers = legacyBorrowerRepo.findAll();
        Map<String, Borrower> borrowerMap = new HashMap<>();

        for (LegacyBorrower legacy : legacyBorrowers) {
            try {
                Borrower borrower = new Borrower();
                borrower.setExternalId(legacy.getBorrowerId());
                borrower.setFirstName(legacy.getFirstName());
                borrower.setLastName(legacy.getLastName());
                borrower.setMiddleInitial(legacy.getMiddleInitial());
                borrower.setSsnHash(legacy.getSsnEncrypted());
                borrower.setDateOfBirth(parseLegacyDate(legacy.getDateOfBirth()));
                borrower.setAddressLine1(legacy.getAddressLine1());
                borrower.setAddressLine2(legacy.getAddressLine2());
                borrower.setCity(legacy.getCity());
                borrower.setState(legacy.getStateCode());
                borrower.setZipCode(legacy.getZipCode());
                borrower.setPhone(legacy.getPhoneNumber());
                borrower.setEmail(legacy.getEmail());
                borrower.setCreditScore(parseInteger(legacy.getCreditScore()));
                borrower.setEmploymentStatus(legacy.getEmploymentStatus());
                borrower.setAnnualIncome(parseAmount(legacy.getAnnualIncome()));
                borrower.setStatus(expandBorrowerStatus(legacy.getStatusCode()));
                borrower.setCreatedAt(parseLegacyDateTime(legacy.getCreatedDate()));
                borrower.setUpdatedAt(parseLegacyDateTime(legacy.getUpdatedDate()));

                Borrower saved = borrowerRepo.save(borrower);
                borrowerMap.put(legacy.getBorrowerId(), saved);
                result.borrowersMigrated++;
            } catch (Exception e) {
                log.error("Failed to migrate borrower {}: {}", legacy.getBorrowerId(), e.getMessage());
                result.errors.add("Borrower " + legacy.getBorrowerId() + ": " + e.getMessage());
            }
        }

        log.info("Migrated {} borrowers", result.borrowersMigrated);
        return borrowerMap;
    }

    private Map<String, LoanProduct> migrateLoanProducts(MigrationResult result) {
        List<LegacyLoanProduct> legacyProducts = legacyProductRepo.findAll();
        Map<String, LoanProduct> productMap = new HashMap<>();

        for (LegacyLoanProduct legacy : legacyProducts) {
            try {
                LoanProduct product = new LoanProduct();
                product.setCode(legacy.getProductCode());
                product.setName(legacy.getDescription());
                product.setType(legacy.getTypeCode());
                product.setTermMonths(parseInteger(legacy.getTermMonths()));
                product.setRateType(legacy.getRateType());
                product.setMinAmount(parseAmount(legacy.getMinAmount()));
                product.setMaxAmount(parseAmount(legacy.getMaxAmount()));
                product.setIsActive("ACT".equals(legacy.getStatusCode()));
                product.setEffectiveDate(parseLegacyDate(legacy.getEffectiveDate()));
                product.setExpirationDate(parseLegacyDate(legacy.getExpirationDate()));

                LoanProduct saved = productRepo.save(product);
                productMap.put(legacy.getProductCode(), saved);
                result.productsMigrated++;
            } catch (Exception e) {
                log.error("Failed to migrate product {}: {}", legacy.getProductCode(), e.getMessage());
                result.errors.add("Product " + legacy.getProductCode() + ": " + e.getMessage());
            }
        }

        log.info("Migrated {} loan products", result.productsMigrated);
        return productMap;
    }

    private Map<String, LoanAccount> migrateLoanAccounts(Map<String, Borrower> borrowerMap,
                                                          Map<String, LoanProduct> productMap,
                                                          MigrationResult result) {
        List<LegacyLoanAccount> legacyAccounts = legacyAccountRepo.findAll();
        Map<String, LoanAccount> accountMap = new HashMap<>();

        for (LegacyLoanAccount legacy : legacyAccounts) {
            try {
                Borrower borrower = borrowerMap.get(legacy.getBorrowerId());
                if (borrower == null) {
                    result.errors.add("Account " + legacy.getLoanAccountNumber()
                            + ": borrower not found for ID " + legacy.getBorrowerId());
                    continue;
                }

                LoanProduct product = productMap.get(legacy.getProductCode());
                if (product == null) {
                    result.errors.add("Account " + legacy.getLoanAccountNumber()
                            + ": product not found for code " + legacy.getProductCode());
                    continue;
                }

                LoanAccount account = new LoanAccount();
                account.setAccountNumber(legacy.getLoanAccountNumber());
                account.setBorrower(borrower);
                account.setProduct(product);
                account.setOriginalAmount(parseAmount(legacy.getOriginalAmount()));
                account.setCurrentBalance(parseAmount(legacy.getCurrentBalance()));
                account.setInterestRate(parseDecimal(legacy.getInterestRate()));
                account.setTermMonths(parseInteger(legacy.getTermMonths()));
                account.setMonthlyPayment(parseAmount(legacy.getMonthlyPayment()));
                account.setOriginationDate(parseLegacyDate(legacy.getOriginationDate()));
                account.setMaturityDate(parseLegacyDate(legacy.getMaturityDate()));
                account.setFirstPaymentDate(parseLegacyDate(legacy.getFirstPaymentDate()));
                account.setNextPaymentDate(parseLegacyDate(legacy.getNextPaymentDate()));
                account.setStatus(expandLoanStatus(legacy.getStatusCode()));
                account.setDelinquencyDays(parseInteger(legacy.getDelinquencyDays()));
                account.setEscrowBalance(parseAmount(legacy.getEscrowBalance()));
                account.setLtvPercent(parseDecimal(legacy.getLtvPercent()));
                account.setPropertyAddress(legacy.getPropertyAddress());
                account.setPropertyCity(legacy.getPropertyCity());
                account.setPropertyState(legacy.getPropertyState());
                account.setPropertyZip(legacy.getPropertyZip());
                account.setPropertyType(expandPropertyType(legacy.getPropertyType()));
                account.setAppraisedValue(parseAmount(legacy.getAppraisedValue()));
                account.setCreatedAt(parseLegacyDateTime(legacy.getCreatedDate()));
                account.setUpdatedAt(parseLegacyDateTime(legacy.getUpdatedDate()));

                LoanAccount saved = accountRepo.save(account);
                accountMap.put(legacy.getLoanAccountNumber(), saved);
                result.accountsMigrated++;
            } catch (Exception e) {
                log.error("Failed to migrate account {}: {}", legacy.getLoanAccountNumber(), e.getMessage());
                result.errors.add("Account " + legacy.getLoanAccountNumber() + ": " + e.getMessage());
            }
        }

        log.info("Migrated {} loan accounts", result.accountsMigrated);
        return accountMap;
    }

    private void migratePayments(Map<String, LoanAccount> accountMap, MigrationResult result) {
        List<LegacyPayment> legacyPayments = legacyPaymentRepo.findAll();

        for (LegacyPayment legacy : legacyPayments) {
            try {
                LoanAccount account = accountMap.get(legacy.getLoanAccountNumber());
                if (account == null) {
                    result.errors.add("Payment " + legacy.getPaymentSequenceNumber()
                            + ": loan account not found for " + legacy.getLoanAccountNumber());
                    continue;
                }

                Payment payment = new Payment();
                payment.setLoanAccount(account);
                payment.setPaymentDate(parseLegacyDate(legacy.getPaymentDate()));
                payment.setTotalAmount(parseAmount(legacy.getTotalAmount()));
                payment.setPrincipalAmount(parseAmount(legacy.getPrincipalAmount()));
                payment.setInterestAmount(parseAmount(legacy.getInterestAmount()));
                payment.setEscrowAmount(parseAmount(legacy.getEscrowAmount()));
                payment.setLateFee(parseAmount(legacy.getLateFee()));
                payment.setType(expandPaymentType(legacy.getTypeCode()));
                payment.setStatus(expandPaymentStatus(legacy.getStatusCode()));
                payment.setReceivedDate(parseLegacyDate(legacy.getReceivedDate()));
                payment.setProcessedDate(parseLegacyDate(legacy.getProcessedDate()));
                payment.setCreatedAt(parseLegacyDateTime(legacy.getCreatedDate()));
                payment.setUpdatedAt(parseLegacyDateTime(legacy.getUpdatedDate()));

                paymentRepo.save(payment);
                result.paymentsMigrated++;
            } catch (Exception e) {
                log.error("Failed to migrate payment {}: {}", legacy.getPaymentSequenceNumber(), e.getMessage());
                result.errors.add("Payment " + legacy.getPaymentSequenceNumber() + ": " + e.getMessage());
            }
        }

        log.info("Migrated {} payments", result.paymentsMigrated);
    }

    // =========================================================================
    // Type conversion helpers per column_mappings.md
    // =========================================================================

    private LocalDate parseLegacyDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        return LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT);
    }

    private LocalDateTime parseLegacyDateTime(String dateStr) {
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
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value.trim());
    }

    // =========================================================================
    // Status code expansion per column_mappings.md
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

    // =========================================================================
    // Migration result tracking
    // =========================================================================

    public static class MigrationResult {
        public int borrowersMigrated;
        public int productsMigrated;
        public int accountsMigrated;
        public int paymentsMigrated;
        public final java.util.List<String> errors = new java.util.ArrayList<>();

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        @Override
        public String toString() {
            return "MigrationResult{" +
                    "borrowers=" + borrowersMigrated +
                    ", products=" + productsMigrated +
                    ", accounts=" + accountsMigrated +
                    ", payments=" + paymentsMigrated +
                    ", errors=" + errors.size() +
                    '}';
        }
    }
}
