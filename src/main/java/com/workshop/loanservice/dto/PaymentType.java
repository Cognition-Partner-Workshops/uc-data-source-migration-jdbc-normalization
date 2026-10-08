package com.workshop.loanservice.dto;

import java.util.Arrays;
import java.util.Optional;

/**
 * Payment types, mapping legacy CDW codes to display labels.
 */
public enum PaymentType {
    REGULAR("REG", "Regular"),
    EXTRA("EXT", "Extra"),
    PARTIAL("PRT", "Partial"),
    PREPAYMENT("PRE", "Prepayment");

    private final String legacyCode;
    private final String label;

    PaymentType(String legacyCode, String label) {
        this.legacyCode = legacyCode;
        this.label = label;
    }

    public String getLegacyCode() { return legacyCode; }
    public String getLabel() { return label; }

    public static Optional<PaymentType> fromLegacyCode(String code) {
        return Arrays.stream(values())
                .filter(t -> t.legacyCode.equals(code))
                .findFirst();
    }

    /**
     * Resolves a user-supplied value, accepting the enum name ("REGULAR"),
     * legacy code ("REG") or label ("Regular"), case-insensitively.
     */
    public static Optional<PaymentType> fromUserValue(String value) {
        if (value == null) return Optional.empty();
        String v = value.trim();
        return Arrays.stream(values())
                .filter(t -> t.name().equalsIgnoreCase(v)
                        || t.legacyCode.equalsIgnoreCase(v)
                        || t.label.equalsIgnoreCase(v))
                .findFirst();
    }
}
