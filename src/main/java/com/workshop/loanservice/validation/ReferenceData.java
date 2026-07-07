package com.workshop.loanservice.validation;

import java.util.Set;

/**
 * Cross-record lookup sets used by validators to check referential integrity
 * across the denormalized legacy tables (which have no FK constraints).
 *
 * @param borrowerIds        every {@code BORR_ID} present in {@code CDW_BORR_MSTR}
 * @param productCodes       every {@code PROD_CD} present in {@code CDW_LN_PROD}
 * @param loanAccountNumbers every {@code LN_ACCT_NBR} present in {@code CDW_LN_ACCT}
 */
public record ReferenceData(
        Set<String> borrowerIds,
        Set<String> productCodes,
        Set<String> loanAccountNumbers) {

    public static ReferenceData empty() {
        return new ReferenceData(Set.of(), Set.of(), Set.of());
    }
}
