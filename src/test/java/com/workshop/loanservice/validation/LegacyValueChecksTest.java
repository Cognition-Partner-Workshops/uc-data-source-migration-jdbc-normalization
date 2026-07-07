package com.workshop.loanservice.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyValueChecksTest {

    @Test
    void isValidDateAcceptsWellFormedMdy() {
        assertTrue(LegacyValueChecks.isValidDate("03/15/1978"));
        assertTrue(LegacyValueChecks.isValidDate("12/31/2099"));
    }

    @Test
    void isValidDateRejectsBadFormatsAndImpossibleDates() {
        assertFalse(LegacyValueChecks.isValidDate("1978-03-15"));   // ISO, not MM/DD/YYYY
        assertFalse(LegacyValueChecks.isValidDate("13/01/2020"));   // month 13
        assertFalse(LegacyValueChecks.isValidDate("02/30/2020"));   // Feb 30
        assertFalse(LegacyValueChecks.isValidDate("3/5/1978"));     // not zero-padded
        assertFalse(LegacyValueChecks.isValidDate(""));
        assertFalse(LegacyValueChecks.isValidDate(null));
    }

    @Test
    void isParseableAmountStripsCommaGrouping() {
        assertTrue(LegacyValueChecks.isParseableAmount("285,000"));
        assertTrue(LegacyValueChecks.isParseableAmount("1,487.02"));
        assertTrue(LegacyValueChecks.isParseableAmount("0.00"));
        assertFalse(LegacyValueChecks.isParseableAmount("$285,000"));
        assertFalse(LegacyValueChecks.isParseableAmount("N/A"));
        assertFalse(LegacyValueChecks.isParseableAmount(null));
    }

    @Test
    void parseAmountRemovesCommas() {
        assertEquals(0, LegacyValueChecks.parseAmountOrNull("1,487.02")
                .compareTo(new java.math.BigDecimal("1487.02")));
        assertNull(LegacyValueChecks.parseAmountOrNull("bogus"));
    }

    @Test
    void isParseableIntegerAndDecimal() {
        assertTrue(LegacyValueChecks.isParseableInteger("360"));
        assertFalse(LegacyValueChecks.isParseableInteger("36.0"));
        assertTrue(LegacyValueChecks.isParseableDecimal("5.250"));
        assertFalse(LegacyValueChecks.isParseableDecimal("5.2.5"));
    }

    @Test
    void emailValidation() {
        assertTrue(LegacyValueChecks.isValidEmail("j.mitchell@email.com"));
        assertFalse(LegacyValueChecks.isValidEmail("not-an-email"));
        assertFalse(LegacyValueChecks.isValidEmail("missing@domain"));
    }
}
