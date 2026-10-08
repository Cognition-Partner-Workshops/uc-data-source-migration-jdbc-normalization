package com.workshop.loanservice.service;

import com.workshop.loanservice.dto.BorrowerDto;
import com.workshop.loanservice.dto.LoanSummaryDto;
import com.workshop.loanservice.dto.PagedResponse;
import com.workshop.loanservice.dto.PaymentDto;
import com.workshop.loanservice.dto.PaymentType;
import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyLoanProduct;
import com.workshop.loanservice.entity.LegacyPayment;
import com.workshop.loanservice.exception.InvalidRequestException;
import com.workshop.loanservice.exception.ResourceNotFoundException;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service layer that reads from legacy tables and translates
 * cryptic legacy fields into clean DTOs.
 *
 * MIGRATION TASK: This service contains all the translation logic
 * between legacy string-typed fields and proper Java types.
 * When switching data sources, this layer needs to be updated
 * (or replaced) to read from the modern schema.
 */
@Service
public class LoanService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private static final DateTimeFormatter LEGACY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private final LegacyBorrowerRepository borrowerRepository;
    private final LegacyLoanAccountRepository loanAccountRepository;
    private final LegacyLoanProductRepository loanProductRepository;
    private final LegacyPaymentRepository paymentRepository;

    public LoanService(LegacyBorrowerRepository borrowerRepository,
                       LegacyLoanAccountRepository loanAccountRepository,
                       LegacyLoanProductRepository loanProductRepository,
                       LegacyPaymentRepository paymentRepository) {
        this.borrowerRepository = borrowerRepository;
        this.loanAccountRepository = loanAccountRepository;
        this.loanProductRepository = loanProductRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<LoanSummaryDto> getAllLoans() {
        Map<String, LegacyLoanProduct> products = loanProductRepository.findAll()
                .stream()
                .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));

        return loanAccountRepository.findAll().stream()
                .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
                .collect(Collectors.toList());
    }

    public LoanSummaryDto getLoanById(String loanAccountNumber) {
        LegacyLoanAccount acct = loanAccountRepository.findById(loanAccountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found: " + loanAccountNumber));
        LegacyLoanProduct product = loanProductRepository.findById(acct.getProductCode())
                .orElse(null);
        return toLoanSummary(acct, product);
    }

    public List<BorrowerDto> getAllBorrowers() {
        return borrowerRepository.findAll().stream()
                .map(this::toBorrowerDto)
                .collect(Collectors.toList());
    }

    public BorrowerDto getBorrowerById(String borrowerId) {
        LegacyBorrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException("Borrower not found: " + borrowerId));
        BorrowerDto dto = toBorrowerDto(borrower);

        // Attach loans for this borrower
        Map<String, LegacyLoanProduct> products = loanProductRepository.findAll()
                .stream()
                .collect(Collectors.toMap(LegacyLoanProduct::getProductCode, p -> p));
        List<LoanSummaryDto> loans = loanAccountRepository.findByBorrowerId(borrowerId)
                .stream()
                .map(acct -> toLoanSummary(acct, products.get(acct.getProductCode())))
                .collect(Collectors.toList());
        dto.setLoans(loans);

        return dto;
    }

    /**
     * Returns a page of a loan's payment history, newest first.
     *
     * Legacy payment dates are MM/DD/YYYY strings, so they cannot be range-compared
     * or sorted correctly in SQL; filtering, sorting and paging happen in memory
     * over the (small) per-loan payment set.
     *
     * @param startDate inclusive lower bound on payment date, or null
     * @param endDate   inclusive upper bound on payment date, or null
     * @param types     comma-separated payment types (e.g. "REG,Extra,PREPAYMENT"), or null for all
     */
    public PagedResponse<PaymentDto> getPaymentHistory(String loanAccountNumber, LocalDate startDate,
                                                       LocalDate endDate, String types, int page, int size) {
        validatePagination(page, size);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidRequestException(
                    "startDate (" + startDate + ") must not be after endDate (" + endDate + ")");
        }
        Set<PaymentType> typeFilter = parsePaymentTypes(types);

        if (!loanAccountRepository.existsById(loanAccountNumber)) {
            throw new ResourceNotFoundException("Loan not found: " + loanAccountNumber);
        }

        boolean dateFiltered = startDate != null || endDate != null;
        List<LegacyPayment> matching = paymentRepository.findByLoanAccountNumber(loanAccountNumber)
                .stream()
                .filter(p -> typeFilter.isEmpty() || PaymentType.fromLegacyCode(p.getTypeCode())
                        .map(typeFilter::contains).orElse(false))
                .filter(p -> !dateFiltered || isWithinRange(parseLegacyDate(p.getPaymentDate()), startDate, endDate))
                .sorted(Comparator
                        .comparing((LegacyPayment p) -> parseLegacyDate(p.getPaymentDate()),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LegacyPayment::getPaymentSequenceNumber,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        List<PaymentDto> content = matching.stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toPaymentDto)
                .collect(Collectors.toList());

        return new PagedResponse<>(content, page, size, matching.size());
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private Set<PaymentType> parsePaymentTypes(String types) {
        Set<PaymentType> result = EnumSet.noneOf(PaymentType.class);
        if (types == null || types.isBlank()) return result;
        for (String raw : types.split(",")) {
            if (raw.isBlank()) continue;
            result.add(PaymentType.fromUserValue(raw).orElseThrow(() -> new InvalidRequestException(
                    "Invalid payment type '" + raw.trim() + "'. Allowed values: "
                            + Arrays.toString(PaymentType.values()))));
        }
        return result;
    }

    private static boolean isWithinRange(LocalDate date, LocalDate startDate, LocalDate endDate) {
        if (date == null) return false;
        return (startDate == null || !date.isBefore(startDate))
                && (endDate == null || !date.isAfter(endDate));
    }

    // =========================================================================
    // LEGACY TRANSLATION METHODS
    // These methods handle the messy conversion from legacy string fields
    // to proper types. After migration, these should be simplified or removed.
    // =========================================================================

    private LoanSummaryDto toLoanSummary(LegacyLoanAccount acct, LegacyLoanProduct product) {
        LoanSummaryDto dto = new LoanSummaryDto();
        dto.setLoanAccountNumber(acct.getLoanAccountNumber());
        dto.setBorrowerName(acct.getBorrowerFirstName() + " " + acct.getBorrowerLastName());
        dto.setProductDescription(product != null ? product.getDescription() : acct.getProductCode());
        dto.setOriginalAmount(parseLegacyAmount(acct.getOriginalAmount()));
        dto.setCurrentBalance(parseLegacyAmount(acct.getCurrentBalance()));
        dto.setInterestRate(parseLegacyDecimal(acct.getInterestRate()));
        dto.setMonthlyPayment(parseLegacyAmount(acct.getMonthlyPayment()));
        dto.setStatus(expandStatusCode(acct.getStatusCode()));
        dto.setOriginationDate(acct.getOriginationDate());
        dto.setPropertyAddress(acct.getPropertyAddress() + ", " + acct.getPropertyCity()
                + ", " + acct.getPropertyState() + " " + acct.getPropertyZip());
        dto.setPropertyType(expandPropertyType(acct.getPropertyType()));
        return dto;
    }

    private BorrowerDto toBorrowerDto(LegacyBorrower borrower) {
        BorrowerDto dto = new BorrowerDto();
        dto.setId(borrower.getBorrowerId());
        String middle = borrower.getMiddleInitial() != null ? " " + borrower.getMiddleInitial() + "." : "";
        dto.setFullName(borrower.getFirstName() + middle + " " + borrower.getLastName());
        dto.setEmail(borrower.getEmail());
        dto.setPhone(borrower.getPhoneNumber());
        dto.setCity(borrower.getCity());
        dto.setState(borrower.getStateCode());
        dto.setCreditScore(parseLegacyInteger(borrower.getCreditScore()));
        dto.setEmploymentStatus(borrower.getEmploymentStatus());
        return dto;
    }

    private PaymentDto toPaymentDto(LegacyPayment pmt) {
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(pmt.getPaymentSequenceNumber());
        dto.setLoanAccountNumber(pmt.getLoanAccountNumber());
        dto.setPaymentDate(pmt.getPaymentDate());
        dto.setTotalAmount(parseLegacyAmount(pmt.getTotalAmount()));
        dto.setPrincipalAmount(parseLegacyAmount(pmt.getPrincipalAmount()));
        dto.setInterestAmount(parseLegacyAmount(pmt.getInterestAmount()));
        dto.setEscrowAmount(parseLegacyAmount(pmt.getEscrowAmount()));
        dto.setLateFee(parseLegacyAmount(pmt.getLateFee()));
        dto.setType(expandPaymentType(pmt.getTypeCode()));
        dto.setStatus(expandPaymentStatus(pmt.getStatusCode()));
        return dto;
    }

    /**
     * Parse legacy amount strings like "285,000" or "1,487.02" into BigDecimal.
     */
    private BigDecimal parseLegacyAmount(String amount) {
        if (amount == null || amount.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(amount.replace(",", ""));
    }

    private BigDecimal parseLegacyDecimal(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim());
    }

    private Integer parseLegacyInteger(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value.trim());
    }

    private String expandStatusCode(String code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case "ACT" -> "Active";
            case "CLO" -> "Closed";
            case "DFT" -> "Default";
            case "FRB" -> "Forbearance";
            default -> code;
        };
    }

    private String expandPropertyType(String code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case "SFR" -> "Single Family Residence";
            case "CND" -> "Condominium";
            case "MFR" -> "Multi-Family Residence";
            case "TWN" -> "Townhouse";
            default -> code;
        };
    }

    private String expandPaymentType(String code) {
        if (code == null) return "Unknown";
        return PaymentType.fromLegacyCode(code).map(PaymentType::getLabel).orElse(code);
    }

    /**
     * Parse legacy MM/DD/YYYY date strings; returns null for missing or malformed values.
     */
    private LocalDate parseLegacyDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim(), LEGACY_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String expandPaymentStatus(String code) {
        if (code == null) return "Unknown";
        return switch (code) {
            case "PST" -> "Posted";
            case "REV" -> "Reversed";
            case "NSF" -> "Non-Sufficient Funds";
            case "PND" -> "Pending";
            default -> code;
        };
    }
}
