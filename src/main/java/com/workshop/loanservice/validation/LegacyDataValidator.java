package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validates and sanitizes legacy CDW data at ingestion time.
 * Catches anomalies that would otherwise cause runtime failures
 * or silent data corruption in the service layer.
 */
@Component
public class LegacyDataValidator {

    private static final Logger log = LoggerFactory.getLogger(LegacyDataValidator.class);

    private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final BigDecimal PAYMENT_TOLERANCE = new BigDecimal("0.02");
    private static final Set<String> VALID_LOAN_STATUSES = Set.of("ACT", "CLO", "DFT", "FRB");
    private static final Set<String> VALID_PAYMENT_STATUSES = Set.of("PST", "REV", "NSF", "PND");
    private static final Set<String> VALID_PAYMENT_TYPES = Set.of("REG", "EXT", "PRT", "PRE");
    private static final Set<String> VALID_PROPERTY_TYPES = Set.of("SFR", "CND", "MFR", "TWN");
    private static final Set<String> VALID_BORROWER_STATUSES = Set.of("ACT", "INA");

    // =========================================================================
    // Safe Parsing Methods (with error handling and fallback defaults)
    // =========================================================================

    public BigDecimal safeParseAmount(String amount, String fieldName, String recordId) {
        if (amount == null || amount.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            String cleaned = amount.replace(",", "").replace("$", "").trim();
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Invalid amount in {} for record {}: '{}' — defaulting to 0", fieldName, recordId, amount);
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal safeParseDecimal(String value, String fieldName, String recordId) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid decimal in {} for record {}: '{}' — defaulting to 0", fieldName, recordId, value);
            return BigDecimal.ZERO;
        }
    }

    public Integer safeParseInteger(String value, String fieldName, String recordId) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer in {} for record {}: '{}' — defaulting to null", fieldName, recordId, value);
            return null;
        }
    }

    public LocalDate safeParseLegacyDate(String dateStr, String fieldName, String recordId) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), LEGACY_DATE_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("Invalid date in {} for record {}: '{}' — defaulting to null", fieldName, recordId, dateStr);
            return null;
        }
    }

    public String formatDateToIso(String legacyDate, String fieldName, String recordId) {
        LocalDate parsed = safeParseLegacyDate(legacyDate, fieldName, recordId);
        return parsed != null ? parsed.toString() : legacyDate;
    }

    // =========================================================================
    // Name Validation
    // =========================================================================

    public String safeName(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        return name.trim();
    }

    public String buildFullName(String firstName, String middleInitial, String lastName) {
        String first = safeName(firstName, "[Unknown]");
        String last = safeName(lastName, "[Unknown]");
        String middle = (middleInitial != null && !middleInitial.isBlank())
                ? " " + middleInitial.trim() + "."
                : "";
        return first + middle + " " + last;
    }

    public String buildBorrowerDisplayName(String firstName, String lastName) {
        String first = safeName(firstName, "[Unknown]");
        String last = safeName(lastName, "[Unknown]");
        return first + " " + last;
    }

    // =========================================================================
    // Status Code Validation
    // =========================================================================

    public String validateLoanStatus(String statusCode, String recordId) {
        if (statusCode == null || statusCode.isBlank()) {
            log.warn("Null/blank loan status for record {} — defaulting to 'ACT'", recordId);
            return "ACT";
        }
        String trimmed = statusCode.trim().toUpperCase();
        if (!VALID_LOAN_STATUSES.contains(trimmed)) {
            log.warn("Invalid loan status '{}' for record {} — passing through as-is", statusCode, recordId);
        }
        return trimmed;
    }

    public String validatePaymentStatus(String statusCode, String recordId) {
        if (statusCode == null || statusCode.isBlank()) {
            log.warn("Null/blank payment status for record {} — defaulting to 'PND'", recordId);
            return "PND";
        }
        String trimmed = statusCode.trim().toUpperCase();
        if (!VALID_PAYMENT_STATUSES.contains(trimmed)) {
            log.warn("Invalid payment status '{}' for record {}", statusCode, recordId);
        }
        return trimmed;
    }

    public String validatePaymentType(String typeCode, String recordId) {
        if (typeCode == null || typeCode.isBlank()) {
            log.warn("Null/blank payment type for record {} — defaulting to 'REG'", recordId);
            return "REG";
        }
        String trimmed = typeCode.trim().toUpperCase();
        if (!VALID_PAYMENT_TYPES.contains(trimmed)) {
            log.warn("Invalid payment type '{}' for record {}", typeCode, recordId);
        }
        return trimmed;
    }

    public String validateBorrowerStatus(String statusCode, String recordId) {
        if (statusCode == null || statusCode.isBlank()) {
            log.warn("Null/blank borrower status for record {} — defaulting to 'ACT'", recordId);
            return "ACT";
        }
        String trimmed = statusCode.trim().toUpperCase();
        if (!VALID_BORROWER_STATUSES.contains(trimmed)) {
            log.warn("Invalid borrower status '{}' for record {}", statusCode, recordId);
        }
        return trimmed;
    }

    // =========================================================================
    // Cross-Field Validation
    // =========================================================================

    public List<String> validatePaymentComponents(LegacyPayment pmt) {
        List<String> warnings = new ArrayList<>();
        String id = pmt.getPaymentSequenceNumber();

        BigDecimal total = safeParseAmount(pmt.getTotalAmount(), "PMT_AMT", id);
        BigDecimal principal = safeParseAmount(pmt.getPrincipalAmount(), "PMT_PRIN_AMT", id);
        BigDecimal interest = safeParseAmount(pmt.getInterestAmount(), "PMT_INT_AMT", id);
        BigDecimal escrow = safeParseAmount(pmt.getEscrowAmount(), "PMT_ESCROW_AMT", id);

        BigDecimal componentSum = principal.add(interest).add(escrow);
        BigDecimal delta = componentSum.subtract(total).abs();

        if (delta.compareTo(PAYMENT_TOLERANCE) > 0) {
            String msg = String.format(
                    "Payment %s: component sum (%.2f) != total (%.2f), delta = %.2f",
                    id, componentSum, total, delta);
            warnings.add(msg);
            log.warn(msg);
        }

        return warnings;
    }

    public List<String> validateDelinquencyStatus(LegacyLoanAccount acct) {
        List<String> warnings = new ArrayList<>();
        String id = acct.getLoanAccountNumber();

        Integer dlqDays = safeParseInteger(acct.getDelinquencyDays(), "LN_DLQ_DAYS", id);
        String status = acct.getStatusCode();

        if (dlqDays != null && dlqDays > 0 && "ACT".equals(status)) {
            String msg = String.format(
                    "Loan %s: %d delinquency days but status is 'ACT' — possible status inconsistency",
                    id, dlqDays);
            warnings.add(msg);
            log.warn(msg);
        }

        return warnings;
    }

    public List<String> validateBorrowerRecord(LegacyBorrower borrower) {
        List<String> warnings = new ArrayList<>();
        String id = borrower.getBorrowerId();

        if (borrower.getFirstName() == null || borrower.getFirstName().isBlank()) {
            warnings.add("Borrower " + id + ": missing first name");
        }
        if (borrower.getLastName() == null || borrower.getLastName().isBlank()) {
            warnings.add("Borrower " + id + ": missing last name");
        }
        if (borrower.getEmail() == null || borrower.getEmail().isBlank()) {
            warnings.add("Borrower " + id + ": missing email");
        }

        validateBorrowerStatus(borrower.getStatusCode(), id);
        safeParseLegacyDate(borrower.getDateOfBirth(), "BORR_DOB_DT", id);
        safeParseInteger(borrower.getCreditScore(), "BORR_CRDT_SCR", id);
        safeParseAmount(borrower.getAnnualIncome(), "BORR_ANN_INCM", id);

        return warnings;
    }

    public List<String> validateLoanAccount(LegacyLoanAccount acct, Set<String> validBorrowerIds, Set<String> validProductCodes) {
        List<String> warnings = new ArrayList<>();
        String id = acct.getLoanAccountNumber();

        if (acct.getBorrowerId() == null || !validBorrowerIds.contains(acct.getBorrowerId())) {
            warnings.add(String.format("Loan %s: borrower '%s' not found in CDW_BORR_MSTR", id, acct.getBorrowerId()));
            log.warn("Orphaned loan {}: borrower '{}' does not exist", id, acct.getBorrowerId());
        }

        if (acct.getProductCode() == null || !validProductCodes.contains(acct.getProductCode())) {
            warnings.add(String.format("Loan %s: product '%s' not found in CDW_LN_PROD", id, acct.getProductCode()));
            log.warn("Orphaned loan {}: product '{}' does not exist", id, acct.getProductCode());
        }

        validateLoanStatus(acct.getStatusCode(), id);
        validateDelinquencyStatus(acct);

        safeParseAmount(acct.getOriginalAmount(), "LN_ORIG_AMT", id);
        safeParseAmount(acct.getCurrentBalance(), "LN_CURR_BAL", id);
        safeParseDecimal(acct.getInterestRate(), "LN_INT_RT", id);
        safeParseAmount(acct.getMonthlyPayment(), "LN_PMT_AMT", id);
        safeParseLegacyDate(acct.getOriginationDate(), "LN_ORIG_DT", id);
        safeParseLegacyDate(acct.getMaturityDate(), "LN_MAT_DT", id);

        return warnings;
    }

    public List<String> validatePayment(LegacyPayment pmt, Set<String> validLoanAccountNumbers) {
        List<String> warnings = new ArrayList<>();
        String id = pmt.getPaymentSequenceNumber();

        if (pmt.getLoanAccountNumber() == null || !validLoanAccountNumbers.contains(pmt.getLoanAccountNumber())) {
            warnings.add(String.format("Payment %s: loan '%s' not found in CDW_LN_ACCT", id, pmt.getLoanAccountNumber()));
            log.warn("Orphaned payment {}: loan '{}' does not exist", id, pmt.getLoanAccountNumber());
        }

        validatePaymentStatus(pmt.getStatusCode(), id);
        validatePaymentType(pmt.getTypeCode(), id);
        warnings.addAll(validatePaymentComponents(pmt));

        safeParseLegacyDate(pmt.getPaymentDate(), "PMT_DT", id);

        return warnings;
    }
}
