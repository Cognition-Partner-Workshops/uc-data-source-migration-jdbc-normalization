package com.workshop.loanservice.dto;

import java.util.Arrays;
import java.util.Optional;

public enum PaymentType {
    REGULAR("REG", "Regular"),
    EXTRA("EXT", "Extra"),
    PARTIAL("PRT", "Partial"),
    PREPAYMENT("PRE", "Prepayment");

    private final String code;
    private final String label;

    PaymentType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<PaymentType> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String candidate = value.trim();
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(candidate)
                        || type.code.equalsIgnoreCase(candidate)
                        || type.label.equalsIgnoreCase(candidate))
                .findFirst();
    }
}
