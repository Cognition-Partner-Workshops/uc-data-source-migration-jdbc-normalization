package com.workshop.loanservice.validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reusable, side-effect-free checks for the loose-typed legacy CDW values.
 *
 * <p>The legacy warehouse stores everything as {@code VARCHAR}: dates as
 * {@code MM/DD/YYYY} strings, money as comma-grouped strings, codes as short
 * mnemonics. These helpers mirror the parsing/expansion logic in
 * {@code LoanService} so the validators can flag data that would fail (or
 * silently coerce) during migration to the modern typed schema.
 */
public final class LegacyValueChecks {

    /** Strict {@code MM/DD/YYYY} parser — rejects impossible dates like 02/30/2020. */
    private static final DateTimeFormatter MDY =
            DateTimeFormatter.ofPattern("MM/dd/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    // Valid status/code domains per data/mappings/column_mappings.md.
    public static final Set<String> LOAN_STATUS_CODES = Set.of("ACT", "CLO", "DFT", "FRB");
    public static final Set<String> BORROWER_STATUS_CODES = Set.of("ACT", "INA");
    public static final Set<String> PRODUCT_STATUS_CODES = Set.of("ACT", "INA");
    public static final Set<String> PROPERTY_TYPE_CODES = Set.of("SFR", "CND", "MFR", "TWN");
    public static final Set<String> PAYMENT_TYPE_CODES = Set.of("REG", "EXT", "PRT", "PRE");
    public static final Set<String> PAYMENT_STATUS_CODES = Set.of("PST", "REV", "NSF", "PND");

    private LegacyValueChecks() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isPresent(String value) {
        return !isBlank(value);
    }

    /** True when {@code value} is a real calendar date in strict {@code MM/DD/YYYY} form. */
    public static boolean isValidDate(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            LocalDate.parse(value.trim(), MDY);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** True when {@code value} parses as a decimal after stripping legacy comma grouping. */
    public static boolean isParseableAmount(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            new BigDecimal(value.trim().replace(",", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** True when {@code value} parses as a plain decimal (no comma grouping expected, e.g. rates). */
    public static boolean isParseableDecimal(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            new BigDecimal(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isParseableInteger(String value) {
        if (isBlank(value)) {
            return false;
        }
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidEmail(String value) {
        return isPresent(value) && EMAIL.matcher(value.trim()).matches();
    }

    /** Parse a comma-grouped legacy amount, or {@code null} when not parseable. */
    public static BigDecimal parseAmountOrNull(String value) {
        if (!isParseableAmount(value)) {
            return null;
        }
        return new BigDecimal(value.trim().replace(",", ""));
    }
}
