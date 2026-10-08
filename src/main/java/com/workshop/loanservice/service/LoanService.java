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
import com.workshop.loanservice.exception.LoanNotFoundException;
import com.workshop.loanservice.repository.LegacyBorrowerRepository;
import com.workshop.loanservice.repository.LegacyLoanAccountRepository;
import com.workshop.loanservice.repository.LegacyLoanProductRepository;
import com.workshop.loanservice.repository.LegacyPaymentRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
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

    private static final DateTimeFormatter PAYMENT_DATE_FORMAT =
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
                .orElseThrow(() -> new LoanNotFoundException(loanAccountNumber));
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
                .orElseThrow(() -> new RuntimeException("Borrower not found: " + borrowerId));
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

    public PagedResponse<PaymentDto> getPaymentHistory(String loanId, String startDate, String endDate,
                                                        String type, int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("page must be non-negative");
        }
        if (size < 1 || size > 100) {
            throw new InvalidRequestException("size must be between 1 and 100");
        }

        LocalDate start = parseRequestDate(startDate, "startDate");
        LocalDate end = parseRequestDate(endDate, "endDate");
        if (start != null && end != null && start.isAfter(end)) {
            throw new InvalidRequestException("startDate must be on or before endDate");
        }
        Set<String> typeCodes = parsePaymentTypeCodes(type);

        if (!loanAccountRepository.existsById(loanId)) {
            throw new LoanNotFoundException(loanId);
        }

        boolean hasDateFilter = start != null || end != null;
        List<PaymentRecord> payments = paymentRepository.findByLoanAccountNumber(loanId).stream()
                .map(payment -> new PaymentRecord(payment, parsePaymentDate(payment.getPaymentDate())))
                .filter(record -> {
                    if (hasDateFilter && record.paymentDate() == null) {
                        return false;
                    }
                    if (start != null && record.paymentDate().isBefore(start)) {
                        return false;
                    }
                    if (end != null && record.paymentDate().isAfter(end)) {
                        return false;
                    }
                    return typeCodes == null || typeCodes.contains(record.payment().getTypeCode());
                })
                .sorted(LoanService::comparePaymentRecords)
                .collect(Collectors.toList());

        long totalElements = payments.size();
        long offset = (long) page * size;
        int fromIndex = (int) Math.min(offset, totalElements);
        int toIndex = (int) Math.min(offset + size, totalElements);
        List<PaymentDto> content = payments.subList(fromIndex, toIndex).stream()
                .map(record -> toPaymentDto(record.payment()))
                .collect(Collectors.toList());
        return new PagedResponse<>(content, page, size, totalElements);
    }

    private LocalDate parseRequestDate(String value, String parameter) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new InvalidRequestException(parameter + " must be a valid ISO date (yyyy-MM-dd)");
        }
    }

    private Set<String> parsePaymentTypeCodes(String type) {
        if (type == null) {
            return null;
        }
        Set<PaymentType> paymentTypes = EnumSet.noneOf(PaymentType.class);
        for (String token : type.split(",", -1)) {
            String candidate = token.trim();
            if (candidate.isEmpty()) {
                throw new InvalidRequestException("type must contain non-blank payment type values");
            }
            PaymentType paymentType = PaymentType.from(candidate)
                    .orElseThrow(() -> new InvalidRequestException(
                            "Unknown payment type '" + candidate + "'. Valid values: " + validPaymentTypes()));
            paymentTypes.add(paymentType);
        }
        if (paymentTypes.isEmpty()) {
            throw new InvalidRequestException("type must contain at least one payment type");
        }
        return paymentTypes.stream()
                .map(PaymentType::getCode)
                .collect(Collectors.toSet());
    }

    private String validPaymentTypes() {
        return java.util.Arrays.stream(PaymentType.values())
                .map(paymentType -> paymentType.name() + " (" + paymentType.getCode()
                        + ", " + paymentType.getLabel() + ")")
                .collect(Collectors.joining(", "));
    }

    private LocalDate parsePaymentDate(String paymentDate) {
        if (paymentDate == null) {
            return null;
        }
        try {
            return LocalDate.parse(paymentDate, PAYMENT_DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static int comparePaymentRecords(PaymentRecord left, PaymentRecord right) {
        LocalDate leftDate = left.paymentDate();
        LocalDate rightDate = right.paymentDate();
        if (leftDate == null && rightDate != null) {
            return 1;
        }
        if (leftDate != null && rightDate == null) {
            return -1;
        }
        if (leftDate != null) {
            int dateComparison = rightDate.compareTo(leftDate);
            if (dateComparison != 0) {
                return dateComparison;
            }
        }
        return compareSequenceNumbersDescending(
                left.payment().getPaymentSequenceNumber(), right.payment().getPaymentSequenceNumber());
    }

    private static int compareSequenceNumbersDescending(String left, String right) {
        if (left == null) {
            return right == null ? 0 : 1;
        }
        if (right == null) {
            return -1;
        }
        try {
            return new BigInteger(right.trim()).compareTo(new BigInteger(left.trim()));
        } catch (NumberFormatException exception) {
            return right.compareTo(left);
        }
    }

    private record PaymentRecord(LegacyPayment payment, LocalDate paymentDate) {
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
        return switch (code) {
            case "REG" -> "Regular";
            case "EXT" -> "Extra";
            case "PRT" -> "Partial";
            case "PRE" -> "Prepayment";
            default -> code;
        };
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
