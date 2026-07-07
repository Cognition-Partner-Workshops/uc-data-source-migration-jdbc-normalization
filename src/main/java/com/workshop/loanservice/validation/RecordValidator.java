package com.workshop.loanservice.validation;

import java.util.List;

/**
 * Validates a single legacy record of type {@code T}, returning any data
 * quality issues found. Implementations are stateless and reusable.
 *
 * @param <T> the legacy entity type this validator inspects
 */
public interface RecordValidator<T> {

    /** Legacy table name this validator targets (e.g. {@code CDW_BORR_MSTR}). */
    String tableName();

    /** Primary-key value of a record, used to label issues. */
    String recordId(T record);

    /**
     * Inspect a record and return all issues found.
     *
     * @param record the legacy entity to validate
     * @param refs   cross-record lookup sets for referential-integrity rules
     * @return issues found; empty when the record is clean
     */
    List<ValidationIssue> validate(T record, ReferenceData refs);
}
