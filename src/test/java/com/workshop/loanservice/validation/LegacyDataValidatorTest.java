package com.workshop.loanservice.validation;

import com.workshop.loanservice.entity.LegacyBorrower;
import com.workshop.loanservice.entity.LegacyLoanAccount;
import com.workshop.loanservice.entity.LegacyPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDataValidatorTest {

    private LegacyDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new LegacyDataValidator();
    }

    // =========================================================================
    // DA-002: Numeric Parsing — NumberFormatException Prevention
    // =========================================================================

    @Nested
    @DisplayName("Safe Amount Parsing (DA-002)")
    class SafeAmountParsing {

        @Test
        @DisplayName("parses comma-formatted amounts correctly")
        void parsesCommaFormattedAmount() {
            assertEquals(new BigDecimal("285000"), validator.safeParseAmount("285,000", "field", "rec"));
            assertEquals(new BigDecimal("1487.02"), validator.safeParseAmount("1,487.02", "field", "rec"));
        }

        @Test
        @DisplayName("strips dollar signs before parsing")
        void stripsDollarSign() {
            assertEquals(new BigDecimal("285000"), validator.safeParseAmount("$285,000", "field", "rec"));
        }

        @Test
        @DisplayName("returns zero for null or blank amounts")
        void returnsZeroForNullOrBlank() {
            assertEquals(BigDecimal.ZERO, validator.safeParseAmount(null, "field", "rec"));
            assertEquals(BigDecimal.ZERO, validator.safeParseAmount("", "field", "rec"));
            assertEquals(BigDecimal.ZERO, validator.safeParseAmount("   ", "field", "rec"));
        }

        @Test
        @DisplayName("returns zero instead of throwing for malformed amounts")
        void returnsZeroForMalformedAmount() {
            assertEquals(BigDecimal.ZERO, validator.safeParseAmount("N/A", "field", "rec"));
            assertEquals(BigDecimal.ZERO, validator.safeParseAmount("TBD", "field", "rec"));
            assertEquals(BigDecimal.ZERO, validator.safeParseAmount("---", "field", "rec"));
            assertEquals(BigDecimal.ZERO, validator.safeParseAmount("PENDING", "field", "rec"));
        }
    }

    @Nested
    @DisplayName("Safe Decimal Parsing (DA-002)")
    class SafeDecimalParsing {

        @Test
        @DisplayName("parses valid decimal strings")
        void parsesValidDecimal() {
            assertEquals(new BigDecimal("5.250"), validator.safeParseDecimal("5.250", "field", "rec"));
            assertEquals(new BigDecimal("82.5"), validator.safeParseDecimal("82.5", "field", "rec"));
        }

        @Test
        @DisplayName("returns zero for malformed decimals")
        void returnsZeroForMalformed() {
            assertEquals(BigDecimal.ZERO, validator.safeParseDecimal("TBD", "field", "rec"));
            assertEquals(BigDecimal.ZERO, validator.safeParseDecimal("N/A", "field", "rec"));
        }
    }

    @Nested
    @DisplayName("Safe Integer Parsing (DA-002)")
    class SafeIntegerParsing {

        @Test
        @DisplayName("parses valid integer strings")
        void parsesValidInteger() {
            assertEquals(745, validator.safeParseInteger("745", "field", "rec"));
            assertEquals(0, validator.safeParseInteger("0", "field", "rec"));
        }

        @Test
        @DisplayName("returns null for null or blank")
        void returnsNullForNullOrBlank() {
            assertNull(validator.safeParseInteger(null, "field", "rec"));
            assertNull(validator.safeParseInteger("", "field", "rec"));
        }

        @Test
        @DisplayName("returns null instead of throwing for malformed integers")
        void returnsNullForMalformed() {
            assertNull(validator.safeParseInteger("N/A", "field", "rec"));
            assertNull(validator.safeParseInteger("abc", "field", "rec"));
            assertNull(validator.safeParseInteger("12.5", "field", "rec"));
        }
    }

    // =========================================================================
    // DA-010: Date Parsing and Validation
    // =========================================================================

    @Nested
    @DisplayName("Date Parsing (DA-010)")
    class DateParsing {

        @Test
        @DisplayName("parses MM/DD/YYYY dates correctly")
        void parsesValidDate() {
            assertNotNull(validator.safeParseLegacyDate("03/15/1978", "field", "rec"));
            assertEquals("1978-03-15", validator.formatDateToIso("03/15/1978", "field", "rec"));
        }

        @Test
        @DisplayName("returns null for invalid dates without throwing")
        void returnsNullForInvalidDate() {
            assertNull(validator.safeParseLegacyDate("13/32/2025", "field", "rec"));
            assertNull(validator.safeParseLegacyDate("00/00/0000", "field", "rec"));
            assertNull(validator.safeParseLegacyDate("TBD", "field", "rec"));
            assertNull(validator.safeParseLegacyDate("2025-01-01", "field", "rec"));
        }

        @Test
        @DisplayName("falls back to original string for unparseable dates in ISO formatting")
        void fallsBackToOriginalStringForIsoFormat() {
            assertEquals("INVALID", validator.formatDateToIso("INVALID", "field", "rec"));
        }
    }

    // =========================================================================
    // DA-008: Null Name Handling
    // =========================================================================

    @Nested
    @DisplayName("Name Validation (DA-008)")
    class NameValidation {

        @Test
        @DisplayName("builds full name correctly with all parts present")
        void buildsFullNameWithAllParts() {
            assertEquals("James R. Mitchell", validator.buildFullName("James", "R", "Mitchell"));
        }

        @Test
        @DisplayName("handles null middle initial")
        void handlesNullMiddleInitial() {
            assertEquals("Robert Williams", validator.buildFullName("Robert", null, "Williams"));
        }

        @Test
        @DisplayName("substitutes [Unknown] for null first or last name")
        void substitutesUnknownForNullNames() {
            assertEquals("[Unknown] R. Mitchell", validator.buildFullName(null, "R", "Mitchell"));
            assertEquals("James [Unknown]", validator.buildFullName("James", null, null));
            assertEquals("[Unknown] [Unknown]", validator.buildFullName(null, null, null));
        }

        @Test
        @DisplayName("substitutes [Unknown] for blank names")
        void substitutesUnknownForBlankNames() {
            assertEquals("[Unknown] R. Mitchell", validator.buildFullName("  ", "R", "Mitchell"));
            assertEquals("[Unknown] [Unknown]", validator.buildBorrowerDisplayName("", ""));
        }
    }

    // =========================================================================
    // DA-004 / DA-003: Status Code Validation
    // =========================================================================

    @Nested
    @DisplayName("Status Code Validation (DA-003/DA-007)")
    class StatusCodeValidation {

        @Test
        @DisplayName("accepts valid loan status codes")
        void acceptsValidLoanStatuses() {
            assertEquals("ACT", validator.validateLoanStatus("ACT", "rec"));
            assertEquals("CLO", validator.validateLoanStatus("CLO", "rec"));
            assertEquals("DFT", validator.validateLoanStatus("DFT", "rec"));
            assertEquals("FRB", validator.validateLoanStatus("FRB", "rec"));
        }

        @Test
        @DisplayName("defaults null/blank loan status to ACT")
        void defaultsNullLoanStatus() {
            assertEquals("ACT", validator.validateLoanStatus(null, "rec"));
            assertEquals("ACT", validator.validateLoanStatus("", "rec"));
        }

        @Test
        @DisplayName("passes through invalid loan status without throwing")
        void passesInvalidLoanStatusWithoutThrowing() {
            String result = validator.validateLoanStatus("XYZ", "rec");
            assertEquals("XYZ", result);
        }

        @Test
        @DisplayName("accepts valid payment statuses")
        void acceptsValidPaymentStatuses() {
            assertEquals("PST", validator.validatePaymentStatus("PST", "rec"));
            assertEquals("REV", validator.validatePaymentStatus("REV", "rec"));
        }

        @Test
        @DisplayName("defaults null/blank payment status to PND")
        void defaultsNullPaymentStatus() {
            assertEquals("PND", validator.validatePaymentStatus(null, "rec"));
        }

        @Test
        @DisplayName("defaults null/blank payment type to REG")
        void defaultsNullPaymentType() {
            assertEquals("REG", validator.validatePaymentType(null, "rec"));
        }
    }

    // =========================================================================
    // DA-001: Payment Component Mismatch Detection
    // =========================================================================

    @Nested
    @DisplayName("Payment Component Validation (DA-001)")
    class PaymentComponentValidation {

        @Test
        @DisplayName("detects payment component mismatch")
        void detectsMismatch() {
            LegacyPayment pmt = new LegacyPayment();
            pmt.setPaymentSequenceNumber("PMT-TEST-001");
            pmt.setTotalAmount("1,487.02");
            pmt.setPrincipalAmount("456.78");
            pmt.setInterestAmount("1,074.69");
            pmt.setEscrowAmount("355.55");
            pmt.setLateFee("0.00");

            List<String> warnings = validator.validatePaymentComponents(pmt);

            assertFalse(warnings.isEmpty(), "Should detect component mismatch");
            assertTrue(warnings.get(0).contains("component sum"));
            assertTrue(warnings.get(0).contains("delta"));
        }

        @Test
        @DisplayName("accepts correctly summing payment components")
        void acceptsCorrectComponents() {
            LegacyPayment pmt = new LegacyPayment();
            pmt.setPaymentSequenceNumber("PMT-TEST-002");
            pmt.setTotalAmount("2,924.18");
            pmt.setPrincipalAmount("1,842.56");
            pmt.setInterestAmount("815.50");
            pmt.setEscrowAmount("266.12");
            pmt.setLateFee("0.00");

            List<String> warnings = validator.validatePaymentComponents(pmt);

            assertTrue(warnings.isEmpty(), "Should not flag matching components");
        }

        @Test
        @DisplayName("tolerates rounding differences within $0.02")
        void toleratesRoundingDifferences() {
            LegacyPayment pmt = new LegacyPayment();
            pmt.setPaymentSequenceNumber("PMT-TEST-003");
            pmt.setTotalAmount("100.00");
            pmt.setPrincipalAmount("60.01");
            pmt.setInterestAmount("40.00");
            pmt.setEscrowAmount("0.00");
            pmt.setLateFee("0.00");

            List<String> warnings = validator.validatePaymentComponents(pmt);

            assertTrue(warnings.isEmpty(), "Should tolerate $0.01 rounding difference");
        }
    }

    // =========================================================================
    // DA-007: Delinquency-Status Inconsistency
    // =========================================================================

    @Nested
    @DisplayName("Delinquency-Status Validation (DA-007)")
    class DelinquencyStatusValidation {

        @Test
        @DisplayName("flags active loan with delinquency days > 0")
        void flagsActiveDelinquentLoan() {
            LegacyLoanAccount acct = new LegacyLoanAccount();
            acct.setLoanAccountNumber("LN-TEST-001");
            acct.setDelinquencyDays("15");
            acct.setStatusCode("ACT");

            List<String> warnings = validator.validateDelinquencyStatus(acct);

            assertFalse(warnings.isEmpty());
            assertTrue(warnings.get(0).contains("delinquency days"));
            assertTrue(warnings.get(0).contains("ACT"));
        }

        @Test
        @DisplayName("does not flag active loan with zero delinquency")
        void doesNotFlagCleanLoan() {
            LegacyLoanAccount acct = new LegacyLoanAccount();
            acct.setLoanAccountNumber("LN-TEST-002");
            acct.setDelinquencyDays("0");
            acct.setStatusCode("ACT");

            List<String> warnings = validator.validateDelinquencyStatus(acct);

            assertTrue(warnings.isEmpty());
        }
    }

    // =========================================================================
    // DA-003: Foreign Key / Orphaned Record Validation
    // =========================================================================

    @Nested
    @DisplayName("FK Validation (DA-003)")
    class ForeignKeyValidation {

        @Test
        @DisplayName("flags loan with non-existent borrower")
        void flagsOrphanedBorrower() {
            LegacyLoanAccount acct = new LegacyLoanAccount();
            acct.setLoanAccountNumber("LN-TEST-001");
            acct.setBorrowerId("B-99999");
            acct.setProductCode("FXD30");
            acct.setStatusCode("ACT");
            acct.setDelinquencyDays("0");
            acct.setOriginalAmount("100,000");
            acct.setCurrentBalance("95,000");
            acct.setInterestRate("4.5");
            acct.setMonthlyPayment("500");
            acct.setOriginationDate("01/01/2020");
            acct.setMaturityDate("01/01/2050");

            Set<String> validBorrowers = Set.of("B-10001", "B-10002");
            Set<String> validProducts = Set.of("FXD30", "FXD15");

            List<String> warnings = validator.validateLoanAccount(acct, validBorrowers, validProducts);

            assertTrue(warnings.stream().anyMatch(w -> w.contains("borrower") && w.contains("not found")));
        }

        @Test
        @DisplayName("flags loan with non-existent product")
        void flagsOrphanedProduct() {
            LegacyLoanAccount acct = new LegacyLoanAccount();
            acct.setLoanAccountNumber("LN-TEST-002");
            acct.setBorrowerId("B-10001");
            acct.setProductCode("INVALID");
            acct.setStatusCode("ACT");
            acct.setDelinquencyDays("0");
            acct.setOriginalAmount("100,000");
            acct.setCurrentBalance("95,000");
            acct.setInterestRate("4.5");
            acct.setMonthlyPayment("500");
            acct.setOriginationDate("01/01/2020");
            acct.setMaturityDate("01/01/2050");

            Set<String> validBorrowers = Set.of("B-10001");
            Set<String> validProducts = Set.of("FXD30", "FXD15");

            List<String> warnings = validator.validateLoanAccount(acct, validBorrowers, validProducts);

            assertTrue(warnings.stream().anyMatch(w -> w.contains("product") && w.contains("not found")));
        }

        @Test
        @DisplayName("flags payment with non-existent loan")
        void flagsOrphanedPayment() {
            LegacyPayment pmt = new LegacyPayment();
            pmt.setPaymentSequenceNumber("PMT-TEST-001");
            pmt.setLoanAccountNumber("LN-NONEXISTENT");
            pmt.setStatusCode("PST");
            pmt.setTypeCode("REG");
            pmt.setTotalAmount("1000");
            pmt.setPrincipalAmount("500");
            pmt.setInterestAmount("500");
            pmt.setEscrowAmount("0");
            pmt.setLateFee("0");
            pmt.setPaymentDate("01/01/2025");

            Set<String> validLoans = Set.of("LN-2019-00142", "LN-2020-00398");

            List<String> warnings = validator.validatePayment(pmt, validLoans);

            assertTrue(warnings.stream().anyMatch(w -> w.contains("loan") && w.contains("not found")));
        }

        @Test
        @DisplayName("does not flag valid references")
        void doesNotFlagValidReferences() {
            LegacyLoanAccount acct = new LegacyLoanAccount();
            acct.setLoanAccountNumber("LN-TEST-003");
            acct.setBorrowerId("B-10001");
            acct.setProductCode("FXD30");
            acct.setStatusCode("ACT");
            acct.setDelinquencyDays("0");
            acct.setOriginalAmount("100,000");
            acct.setCurrentBalance("95,000");
            acct.setInterestRate("4.5");
            acct.setMonthlyPayment("500");
            acct.setOriginationDate("01/01/2020");
            acct.setMaturityDate("01/01/2050");

            Set<String> validBorrowers = Set.of("B-10001");
            Set<String> validProducts = Set.of("FXD30");

            List<String> warnings = validator.validateLoanAccount(acct, validBorrowers, validProducts);

            assertTrue(warnings.stream().noneMatch(w -> w.contains("not found")));
        }
    }

    // =========================================================================
    // DA-008: Borrower Record Validation
    // =========================================================================

    @Nested
    @DisplayName("Borrower Record Validation (DA-008)")
    class BorrowerRecordValidation {

        @Test
        @DisplayName("flags missing first name")
        void flagsMissingFirstName() {
            LegacyBorrower borrower = new LegacyBorrower();
            borrower.setBorrowerId("B-TEST-001");
            borrower.setFirstName(null);
            borrower.setLastName("Doe");
            borrower.setEmail("test@test.com");
            borrower.setStatusCode("ACT");
            borrower.setCreditScore("700");

            List<String> warnings = validator.validateBorrowerRecord(borrower);

            assertTrue(warnings.stream().anyMatch(w -> w.contains("missing first name")));
        }

        @Test
        @DisplayName("flags missing last name")
        void flagsMissingLastName() {
            LegacyBorrower borrower = new LegacyBorrower();
            borrower.setBorrowerId("B-TEST-002");
            borrower.setFirstName("John");
            borrower.setLastName(null);
            borrower.setEmail("test@test.com");
            borrower.setStatusCode("ACT");
            borrower.setCreditScore("700");

            List<String> warnings = validator.validateBorrowerRecord(borrower);

            assertTrue(warnings.stream().anyMatch(w -> w.contains("missing last name")));
        }

        @Test
        @DisplayName("flags missing email")
        void flagsMissingEmail() {
            LegacyBorrower borrower = new LegacyBorrower();
            borrower.setBorrowerId("B-TEST-003");
            borrower.setFirstName("John");
            borrower.setLastName("Doe");
            borrower.setEmail(null);
            borrower.setStatusCode("ACT");
            borrower.setCreditScore("700");

            List<String> warnings = validator.validateBorrowerRecord(borrower);

            assertTrue(warnings.stream().anyMatch(w -> w.contains("missing email")));
        }

        @Test
        @DisplayName("clean borrower produces no warnings")
        void cleanBorrowerNoWarnings() {
            LegacyBorrower borrower = new LegacyBorrower();
            borrower.setBorrowerId("B-TEST-004");
            borrower.setFirstName("Jane");
            borrower.setLastName("Smith");
            borrower.setEmail("jane@test.com");
            borrower.setStatusCode("ACT");
            borrower.setCreditScore("750");
            borrower.setDateOfBirth("01/15/1990");
            borrower.setAnnualIncome("100,000");

            List<String> warnings = validator.validateBorrowerRecord(borrower);

            assertTrue(warnings.isEmpty(), "Clean borrower should have no warnings");
        }
    }
}
